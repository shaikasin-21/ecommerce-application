package com.asin.ecommerce.integration;

import com.asin.ecommerce.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** End-to-end API flow against an in-memory H2 database (profile "test"). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Value("${app.admin.email}") String adminEmail;
    @Value("${app.admin.password}") String adminPassword;
    @Value("${app.payment.webhook-secret}") String webhookSecret;

    @Test
    void fullShoppingFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminToken = login(adminEmail, adminPassword);

        long categoryId = json(send(post("/admin/categories"), Map.of("name", "Cat-" + suffix), adminToken, 201))
                .get("id").asLong();
        long productId = json(send(post("/admin/products"), Map.of(
                "name", "Widget-" + suffix, "description", "test", "price", 100.0,
                "stock", 5, "categoryId", categoryId), adminToken, 201)).get("id").asLong();

        // public search + filter
        JsonNode found = json(send(get("/products?search=widget-" + suffix + "&category=" + categoryId), null, null, 200));
        assertThat(found.get("totalElements").asLong()).isEqualTo(1);

        // register a customer
        String email = "user-" + suffix + "@shop.com";
        String userToken = json(send(post("/auth/register"),
                Map.of("name", "Test User", "email", email, "password", "Passw0rd!x"), null, 201)).get("token").asText();

        // customers cannot use admin APIs
        send(post("/admin/products"), Map.of("name", "x", "price", 1, "stock", 1, "categoryId", categoryId), userToken, 403);

        // cart -> order deducts stock
        send(post("/cart/items"), Map.of("productId", productId, "quantity", 2), userToken, 201);
        long orderId = json(send(post("/orders"), null, userToken, 201)).get("id").asLong();
        assertThat(stockOf(productId)).isEqualTo(3);

        // cannot add more than remaining stock
        send(post("/cart/items"), Map.of("productId", productId, "quantity", 4), userToken, 409);

        // payment + signed webhook
        String providerRef = json(send(post("/payments/initiate"), Map.of("orderId", orderId), userToken, 201))
                .get("providerRef").asText();
        String webhookBody = om.writeValueAsString(Map.of("providerRef", providerRef, "status", "SUCCESS"));

        mvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(webhookBody)
                        .header("X-Webhook-Signature", "bad-signature"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(401));
        mvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(webhookBody)
                        .header("X-Webhook-Signature", PaymentService.hmacSha256Hex(webhookBody, webhookSecret)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(json(send(get("/orders/" + orderId), null, userToken, 200)).get("status").asText()).isEqualTo("PAID");

        // cancel -> stock restored
        assertThat(json(send(put("/orders/" + orderId + "/cancel"), null, userToken, 200)).get("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(stockOf(productId)).isEqualTo(5);

        // cancelled orders cannot be shipped
        send(put("/admin/orders/" + orderId + "/status"), Map.of("status", "SHIPPED"), adminToken, 400);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        send(get("/cart"), null, null, 401);
        send(post("/orders"), null, null, 401);
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        send(post("/auth/register"), Map.of("name", "A", "email", "not-an-email", "password", "short"), null, 400);
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        send(post("/auth/login"), Map.of("email", adminEmail, "password", "wrong-password"), null, 401);
    }

    // ---- helpers ----

    private String login(String email, String password) throws Exception {
        return json(send(post("/auth/login"), Map.of("email", email, "password", password), null, 200))
                .get("token").asText();
    }

    private int stockOf(long productId) throws Exception {
        return json(send(get("/products/" + productId), null, null, 200)).get("stock").asInt();
    }

    private MvcResult send(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus)
            throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        MvcResult result = mvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("status for %s (body: %s)", result.getRequest().getRequestURI(), result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return result;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }
}

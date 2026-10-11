package com.sentinel.iot;

import com.sentinel.iot.controller.MqttAuthController;
import com.sentinel.iot.dto.MqttAclRequest;
import com.sentinel.iot.dto.MqttAuthRequest;
import com.sentinel.iot.dto.MqttAuthResponse;
import com.sentinel.iot.service.MqttProvisioningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("MqttAuthController")
@ExtendWith(MockitoExtension.class)
class MqttAuthControllerTest {

    @Mock
    private MqttProvisioningService mqttProvisioningService;

    @InjectMocks
    private MqttAuthController controller;

    @Test
    @DisplayName("authenticate returns allow when credentials are valid")
    void authenticate_validCredentials_returnsAllow() {
        when(mqttProvisioningService.verifyCredentials("sensor-1", "secretPass")).thenReturn(true);

        MqttAuthRequest request = new MqttAuthRequest("client-1", "sensor-1", "secretPass");
        ResponseEntity<MqttAuthResponse> response = controller.authenticate(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo("allow");
        assertThat(response.getBody().isSuperuser()).isFalse();
        verify(mqttProvisioningService).verifyCredentials("sensor-1", "secretPass");
    }

    @Test
    @DisplayName("authenticate returns deny when credentials are invalid")
    void authenticate_invalidCredentials_returnsDeny() {
        when(mqttProvisioningService.verifyCredentials("sensor-1", "wrongPass")).thenReturn(false);

        MqttAuthRequest request = new MqttAuthRequest("client-1", "sensor-1", "wrongPass");
        ResponseEntity<MqttAuthResponse> response = controller.authenticate(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo("deny");
    }

    @Test
    @DisplayName("authenticate returns deny when request is null or missing fields")
    void authenticate_missingFields_returnsDeny() {
        ResponseEntity<MqttAuthResponse> res1 = controller.authenticate(null);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res1.getBody().result()).isEqualTo("deny");

        ResponseEntity<MqttAuthResponse> res2 = controller.authenticate(new MqttAuthRequest("c1", null, "pass"));
        assertThat(res2.getBody()).isNotNull();
        assertThat(res2.getBody().result()).isEqualTo("deny");

        verifyNoInteractions(mqttProvisioningService);
    }

    @Test
    @DisplayName("acl returns allow when topic and action are authorized")
    void acl_authorized_returnsAllow() {
        when(mqttProvisioningService.verifyAcl("sensor-1", "c1", "factory/telemetry", "publish"))
                .thenReturn(true);

        MqttAclRequest request = new MqttAclRequest("c1", "sensor-1", "factory/telemetry", "publish");
        ResponseEntity<MqttAuthResponse> response = controller.acl(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo("allow");
    }

    @Test
    @DisplayName("acl returns deny when topic is unauthorized")
    void acl_unauthorized_returnsDeny() {
        when(mqttProvisioningService.verifyAcl("sensor-1", "c1", "tenants/other/telemetry", "publish"))
                .thenReturn(false);

        MqttAclRequest request = new MqttAclRequest("c1", "sensor-1", "tenants/other/telemetry", "publish");
        ResponseEntity<MqttAuthResponse> response = controller.acl(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo("deny");
    }

    @Test
    @DisplayName("acl returns deny when request is null or missing fields")
    void acl_missingFields_returnsDeny() {
        ResponseEntity<MqttAuthResponse> res1 = controller.acl(null);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res1.getBody().result()).isEqualTo("deny");

        ResponseEntity<MqttAuthResponse> res2 = controller.acl(new MqttAclRequest("c1", null, "topic", "publish"));
        assertThat(res2.getBody()).isNotNull();
        assertThat(res2.getBody().result()).isEqualTo("deny");

        verifyNoInteractions(mqttProvisioningService);
    }
}

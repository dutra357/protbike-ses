package br.com.protbike.service;


import br.com.protbike.strategy.NotificationStrategy;
import br.com.protbike.utils.BoletoTestHelper;
import org.mockito.Mock;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock
    NotificationStrategy emailStrategy;

    @Mock
    NotificationStrategy whatsappStrategy;

    // Mock do container "Instance" do CDI
    @Mock
    Instance<NotificationStrategy> instanceMock;

    NotificationProcessor processor;

    @BeforeEach
    void setup() {
        lenient().when(emailStrategy.getChannelName()).thenReturn("email");
        lenient().when(whatsappStrategy.getChannelName()).thenReturn("whatsapp");

        when(instanceMock.stream()).thenReturn(Stream.of(emailStrategy, whatsappStrategy));

        // Instanciando a classe real e passando o mock do CDI
        processor = new NotificationProcessor(instanceMock);
    }

    @Test
    void deveAcionarApenasWhatsapp() {
        var msg = BoletoTestHelper.builder()
                .withCanais("whatsapp")
                .build();

        processor.processMessage(msg);

        verify(whatsappStrategy).send(msg);
        verify(emailStrategy, never()).send(any());
    }

    @Test
    void deveAcionarAmbosOsCanais() {
        var msg = BoletoTestHelper.builder()
                .withCanais("whatsapp", "email")
                .build();

        processor.processMessage(msg);

        verify(whatsappStrategy).send(msg);
        verify(emailStrategy).send(msg);
    }

    @Test
    void deveIgnorarCanalDesconhecidoSemQuebrar() {

        var msg = BoletoTestHelper.builder()
                .withCanais("sms", "email")
                .build();

        processor.processMessage(msg);

        verify(emailStrategy).send(msg);
        verifyNoMoreInteractions(ignoreStubs(emailStrategy, whatsappStrategy));
    }
}
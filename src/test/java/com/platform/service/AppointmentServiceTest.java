package com.platform.service;

import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class AppointmentServiceTest {

    private AppointmentRepository appointmentRepository;

    @BeforeEach
    public void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
    }

    @Test
    public void testCreateAppointment() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        Appointment appointment = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Initial Assessment", "PENDING");
        appointment.setId(UUID.randomUUID());

        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        Appointment saved = appointmentRepository.save(appointment);
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals("Initial Assessment", saved.getTitle());
    }

    @Test
    public void testFindByClientUuid() {
        UUID clientUuid = UUID.randomUUID();
        Appointment app1 = new Appointment(clientUuid, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Session 1", "CONFIRMED");
        
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(app1));

        List<Appointment> results = appointmentRepository.findByClientUuid(clientUuid);
        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(clientUuid, results.get(0).getClientUuid());
    }
}

package com.platform.crm.component;

import com.platform.crm.model.Role;
import com.platform.crm.model.User;
import com.platform.crm.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            User therapist = new User("therapist", passwordEncoder.encode("password"), Role.THERAPIST);
            User client1 = new User("client1", passwordEncoder.encode("password"), Role.CLIENT);
            User client2 = new User("client2", passwordEncoder.encode("password"), Role.CLIENT);

            userRepository.save(therapist);
            userRepository.save(client1);
            userRepository.save(client2);
            
            System.out.println("Seeded database with default users (therapist, client1, client2) with password 'password'");
        }
    }
}

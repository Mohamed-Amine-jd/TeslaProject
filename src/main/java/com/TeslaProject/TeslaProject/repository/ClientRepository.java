package com.TeslaProject.TeslaProject.repository;

import com.TeslaProject.TeslaProject.models.Client;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findByMatricule(String matricule);
}
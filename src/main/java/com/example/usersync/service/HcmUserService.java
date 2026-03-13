package com.example.usersync.service;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;

import java.util.List;

/**
 * Service interface for HCM user operations.
 */
public interface HcmUserService {

    /**
     * Search HCM users with pagination.
     *
     * @param request the search request containing pagination info
     * @return the search response with HCM user records
     */
    HcmUserSearchResponse search(HcmUserSearchRequest request);

    /**
     * Create a new HCM user record.
     *
     * @param user the user data to create
     * @return the created user with recordId
     */
    HcmUserDto create(HcmUserDto user);

    /**
     * Update an existing HCM user record.
     *
     * @param user the user data to update (must contain recordId)
     * @return the updated user
     */
    HcmUserDto update(HcmUserDto user);

    /**
     * Delete HCM user records by record IDs.
     *
     * @param recordIds the list of record IDs to delete
     * @return the number of records deleted
     */
    int delete(List<String> recordIds);

    /**
     * Delete a single HCM user record by record ID.
     *
     * @param recordId the record ID to delete
     * @return true if deletion was successful
     */
    boolean delete(String recordId);

    /**
     * Onboard a new HCM user (create user and send Kafka event).
     *
     * @param user the user data to onboard
     * @return the created user with recordId
     */
    HcmUserDto onboard(HcmUserDto user);
}

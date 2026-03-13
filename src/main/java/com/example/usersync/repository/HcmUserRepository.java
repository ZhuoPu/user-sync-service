package com.example.usersync.repository;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;

import java.util.List;

/**
 * Repository interface for HCM user data.
 */
public interface HcmUserRepository {

    /**
     * Search HCM users with pagination and HCM system filter.
     *
     * @param request the search request containing pagination info
     * @return the search response with HCM user records
     */
    HcmUserSearchResponse search(HcmUserSearchRequest request);

    /**
     * Search all users without system filter (for debugging).
     *
     * @param request the search request containing pagination info
     * @return the search response with all user records
     */
    HcmUserSearchResponse searchAll(HcmUserSearchRequest request);

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
}

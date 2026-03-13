package com.example.usersync.service;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchRequest;
import com.example.usersync.dto.IamUserSearchResponse;

import java.util.List;

/**
 * Service interface for IAM user operations.
 */
public interface IamUserService {

    /**
     * Search IAM users with pagination.
     *
     * @param request the search request containing pagination info
     * @return the search response with IAM user records
     */
    IamUserSearchResponse search(IamUserSearchRequest request);

    /**
     * Create a new IAM user record.
     *
     * @param user the user data to create
     * @return the created user with recordId
     */
    IamUserDto create(IamUserDto user);

    /**
     * Update an existing IAM user record.
     *
     * @param user the user data to update (must contain recordId)
     * @return the updated user
     */
    IamUserDto update(IamUserDto user);

    /**
     * Delete IAM user records by record IDs.
     *
     * @param recordIds the list of record IDs to delete
     * @return the number of records deleted
     */
    int delete(List<String> recordIds);

    /**
     * Delete a single IAM user record by record ID.
     *
     * @param recordId the record ID to delete
     * @return true if deletion was successful
     */
    boolean delete(String recordId);

    /**
     * Resign an IAM user (set active=false and prefix phone with _)
     *
     * @param recordId the record ID of the user to resign
     * @return the updated user
     */
    IamUserDto resign(String recordId);
}

package com.caseaxis.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entity, Object id) {
        super(String.format("%s with id '%s' was not found", entity, id));
    }
}

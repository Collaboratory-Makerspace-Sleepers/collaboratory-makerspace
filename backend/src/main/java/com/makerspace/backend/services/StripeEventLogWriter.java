package com.makerspace.backend.services;

import com.makerspace.backend.repository.StripeEventLogRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

@Service
public class StripeEventLogWriter {

    @Autowired private StripeEventLogRepository eventLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsert(String eventId, String type, String apiVersion,
                             boolean livemode, String source) {
          try {
              eventLogRepository.insertReceived(eventId, type, apiVersion, livemode, source);
              return true;
          } catch (DataIntegrityViolationException e) {
              return false;
          }
    }
}

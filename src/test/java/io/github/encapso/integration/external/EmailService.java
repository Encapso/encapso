package io.github.encapso.integration.external;

import java.util.ArrayList;
import java.util.List;

/** Simulates an external email service. Tracks sent emails for test verification. */
public class EmailService {

    private final List<String> sentEmails = new ArrayList<>();

    public void send(String to, String subject) {
        sentEmails.add(to + "|" + subject);
    }

    public List<String> getSentEmails() { return sentEmails; }
}

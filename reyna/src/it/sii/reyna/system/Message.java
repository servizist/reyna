package it.sii.reyna.system;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 6230786319646630263L;

    private Long id;

    private String url;

    private String body;

    private String username;

    private String password;

    private List<Header> headers;

    private Integer numberOfTries;

    public Message(URI uri, String body) {
        this(null, uri, body, null, null, null);
    }

    public Message(URI uri, String body, String username, String password) {
        this(null, uri, body, username, password, null);
    }

    public Message(Long id, URI uri, String body, String username, String password, List<Header> headers) {

        this.id = id;
        this.username = username;
        this.password = password;

        this.url = uri.toString();
        this.body = body;
        this.headers = headers;
        if(this.headers == null) {
            this.headers = new ArrayList<Header>();
        }
    }

    public Long getId() {
        return this.id;
    }

    public String getUrl() {
        return this.url;
    }

    public URI getURI() {
        try {
            return new URI(this.url);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public void addHeader(Header header) {
        this.headers.add(header);
    }

    public String getBody() {
        return this.body;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public Integer getNumberOfTries() {
        return numberOfTries;
    }

    public void setNumberOfTries(Integer numberOfTries) {
        this.numberOfTries = numberOfTries;
    }
}

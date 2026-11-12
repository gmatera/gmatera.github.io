package it.prova.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Legge l'header X-Correlation-Id (o ne genera uno nuovo) e lo rende disponibile ai log
 * per tutta la durata della richiesta. Lo rimanda anche nella risposta.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(CorrelationId.HEADER);
        if (id == null || id.isBlank()) {
            id = CorrelationId.newId();
        }
        CorrelationId.set(id);
        response.setHeader(CorrelationId.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }
}

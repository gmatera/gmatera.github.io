package it.prova.receipts.closure;

public enum ClosureStatus {
    /** da trasmettere (anche in attesa del prossimo tentativo) */
    TO_SEND,
    /** in corso */
    IN_PROGRESS,
    /** trasmessa */
    SENT,
    /** fallita definitivamente, superato il numero massimo di tentativi */
    FAILED
}

package com.custommobsforge.custommobsforge.server.ai;

/**
 * Статусы узла дерева поведения
 */
public enum NodeStatus {
    READY,      // Узел готов к выполнению
    RUNNING,    // Узел выполняется
    SUCCESS,    // Узел успешно завершен
    FAILURE     // Узел завершен с ошибкой
}
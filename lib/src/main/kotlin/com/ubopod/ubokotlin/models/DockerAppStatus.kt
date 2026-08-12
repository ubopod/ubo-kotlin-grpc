package com.ubopod.ubokotlin.models

/** Status of one Docker item, mirrors `Ubo.DockerItemStatus`. */
public enum class DockerItemStatus {
    NOT_AVAILABLE,
    FETCHING,
    AVAILABLE,
    CREATED,
    STARTING,
    RUNNING,
    ERROR,
    PROCESSING,
    UNSPECIFIED,
}

/** Health of one Docker item, mirrors `Ubo.DockerItemHealth`. */
public enum class DockerItemHealth {
    OK,
    RECOVERED,
    CRASH_LOOPING,
    UNSPECIFIED,
}

/**
 * One entry from `DockerServiceState.apps`, sourced from `state.docker.service`.
 *
 * Mirrors `Sources/UboSwift/Models/DockerAppStatus.swift`.
 */
public data class DockerAppStatus(
    val id: String,
    val label: String,
    val icon: String,
    val status: DockerItemStatus,
    val health: DockerItemHealth,
)

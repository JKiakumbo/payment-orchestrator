plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "payment-orchestrator"
include("services")
include("services:payment-service")
include("services:fraud-service")
include("services:funds-service")
include("services:processor-service")
include("services:ledger-service")
package net.vulkanmod.vulkan.memory;

/** CPU-only contract tests for the adaptive Linux MemAvailable safety policy. */
public final class MemoryPressurePolicyTest {
    private static final long DEFAULT_MINIMUM_MIB = 2048L;
    private static final long DEFAULT_PERCENT = 10L;

    private MemoryPressurePolicyTest() {
    }

    public static void main(String[] args) {
        minimumReserveProtectsSmallerSystems();
        reserveScalesWithPhysicalMemory();
        unavailableTotalFallsBackToMinimum();
        pressureDependsOnAvailableMemoryNotProcessSize();
        policyInputsAreClampedSafely();
        System.out.println("Memory pressure policy tests passed");
    }

    private static void minimumReserveProtectsSmallerSystems() {
        require(reserve(8L * 1024L) == DEFAULT_MINIMUM_MIB,
                "8 GiB system should retain the 2 GiB minimum reserve");
        require(reserve(16L * 1024L) == DEFAULT_MINIMUM_MIB,
                "16 GiB system should retain the 2 GiB minimum reserve");
    }

    private static void reserveScalesWithPhysicalMemory() {
        require(reserve(32L * 1024L) == 3276L,
                "32 GiB system should reserve approximately 10 percent");
        require(reserve(64L * 1024L) == 6553L,
                "64 GiB system should reserve approximately 10 percent");
        require(reserve(128L * 1024L) == 13107L,
                "128 GiB system should continue scaling rather than using a fixed ceiling");
    }

    private static void unavailableTotalFallsBackToMinimum() {
        require(reserve(-1L) == DEFAULT_MINIMUM_MIB,
                "unknown MemTotal should fall back to the minimum reserve");
        require(reserve(0L) == DEFAULT_MINIMUM_MIB,
                "zero MemTotal should fall back to the minimum reserve");
    }

    private static void pressureDependsOnAvailableMemoryNotProcessSize() {
        long totalMiB = 32L * 1024L;
        long reserveMiB = reserve(totalMiB);

        require(!pressure(reserveMiB + 1L, totalMiB),
                "available memory above reserve should remain usable");
        require(pressure(reserveMiB, totalMiB),
                "available memory at the reserve should trip the guard");
        require(pressure(reserveMiB - 1L, totalMiB),
                "available memory below reserve should trip the guard");
        require(!pressure(-1L, totalMiB),
                "unavailable MemAvailable telemetry must not create a false OOM");
    }

    private static void policyInputsAreClampedSafely() {
        require(MemoryDiagnostics.calculateSystemAvailableReserveMiB(32768L, -1L, -5L) == 0L,
                "negative reserve settings should clamp to zero");
        require(MemoryDiagnostics.calculateSystemAvailableReserveMiB(32768L, 1024L, 500L) == 32768L,
                "percentage override should clamp to 100 percent");
    }

    private static long reserve(long totalMiB) {
        return MemoryDiagnostics.calculateSystemAvailableReserveMiB(
                totalMiB, DEFAULT_MINIMUM_MIB, DEFAULT_PERCENT);
    }

    private static boolean pressure(long availableMiB, long totalMiB) {
        return MemoryDiagnostics.isSystemMemoryPressure(
                availableMiB, totalMiB, DEFAULT_MINIMUM_MIB, DEFAULT_PERCENT);
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}

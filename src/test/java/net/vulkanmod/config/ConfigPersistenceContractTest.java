package net.vulkanmod.config;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ConfigPersistenceContractTest {
    private ConfigPersistenceContractTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyRecoveryRoundTrip();

        ConfigVisitor config = inspect(
                "net/vulkanmod/config/Config.class", new ConfigVisitor());
        require(config.loadReferencesJsonParseException,
                "Config.load() must handle malformed JSON explicitly");
        require(config.loadQuarantinesMalformedFile,
                "Config.load() must quarantine malformed config before recreating defaults");
        require(config.writeCreatesTempFile,
                "Config.write() must write through a temporary file");
        require(config.loadUsesReplaceExisting,
                "Malformed config quarantine must replace an older .broken backup safely");
        require(config.writeUsesAtomicMove,
                "Config.write() must attempt an atomic replacement");
        require(config.writeCatchesAtomicMoveUnsupported,
                "Config.write() must fall back when atomic replacement is unsupported");
        require(config.writeMoveCalls >= 2,
                "Config.write() must contain both atomic and non-atomic replacement paths");
        require(config.writeUsesReplaceExisting,
                "Config.write() replacement paths must replace the previous config");
        require(config.writeDeletesTempOnFailure,
                "Config.write() must clean temporary files on failure");

        OptionsVisitor options = inspect(
                "net/vulkanmod/config/Options.class", new OptionsVisitor());
        require(options.applySavesMinecraftOptions,
                "Custom options screen must persist vanilla Minecraft options");

        System.out.println("Configuration persistence/recovery contract passed");
    }

    private static void verifyRecoveryRoundTrip() throws Exception {
        Path directory = Files.createTempDirectory("vulkanmod-config-contract-");
        Path configPath = directory.resolve("vulkanmod_settings.json");
        Path brokenPath = directory.resolve("vulkanmod_settings.json.broken");
        String malformed = "{ malformed";

        try {
            Files.writeString(configPath, malformed, StandardCharsets.UTF_8);

            Config loaded = Config.load(configPath);
            require(loaded == null,
                    "Malformed config must return null so Initializer can recreate defaults");
            require(!Files.exists(configPath),
                    "Malformed config must be moved away from the live config path");
            require(Files.exists(brokenPath),
                    "Malformed config must be quarantined as .broken");
            require(malformed.equals(Files.readString(brokenPath, StandardCharsets.UTF_8)),
                    "Malformed config quarantine must preserve the original bytes");

            Config replacement = new Config();
            replacement.write();

            require(Files.isRegularFile(configPath),
                    "Default replacement config must be written after quarantine");
            require(Config.load(configPath) != null,
                    "Replacement config must be readable after atomic/fallback write");
            require(malformed.equals(Files.readString(brokenPath, StandardCharsets.UTF_8)),
                    "Writing defaults must not overwrite the quarantined malformed config");

            try(Stream<Path> files = Files.list(directory)) {
                require(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                        "Successful config replacement must not leave temporary files behind");
            }
        } finally {
            if(Files.exists(directory)) {
                try(Stream<Path> paths = Files.walk(directory)) {
                    for(Path path : paths.sorted(Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = ConfigPersistenceContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ConfigVisitor extends ClassVisitor {
        boolean loadReferencesJsonParseException;
        boolean loadQuarantinesMalformedFile;
        boolean loadUsesReplaceExisting;
        boolean writeCreatesTempFile;
        boolean writeUsesAtomicMove;
        boolean writeCatchesAtomicMoveUnsupported;
        boolean writeUsesReplaceExisting;
        boolean writeDeletesTempOnFailure;
        int writeMoveCalls;

        ConfigVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("load".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start,
                                                   org.objectweb.asm.Label end,
                                                   org.objectweb.asm.Label handler,
                                                   String type) {
                        if("com/google/gson/JsonParseException".equals(type))
                            loadReferencesJsonParseException = true;
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        if(opcode == Opcodes.GETSTATIC
                                && owner.equals("java/nio/file/StandardCopyOption")
                                && fieldName.equals("REPLACE_EXISTING"))
                            loadUsesReplaceExisting = true;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("java/nio/file/Files")
                                && methodName.equals("move"))
                            loadQuarantinesMalformedFile = true;
                    }
                };
            }

            if("write".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start,
                                                   org.objectweb.asm.Label end,
                                                   org.objectweb.asm.Label handler,
                                                   String type) {
                        if("java/nio/file/AtomicMoveNotSupportedException".equals(type))
                            writeCatchesAtomicMoveUnsupported = true;
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        if(opcode != Opcodes.GETSTATIC
                                || !owner.equals("java/nio/file/StandardCopyOption"))
                            return;
                        if(fieldName.equals("ATOMIC_MOVE"))
                            writeUsesAtomicMove = true;
                        if(fieldName.equals("REPLACE_EXISTING"))
                            writeUsesReplaceExisting = true;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("java/nio/file/Files")
                                && methodName.equals("createTempFile"))
                            writeCreatesTempFile = true;
                        if(owner.equals("java/nio/file/Files")
                                && methodName.equals("move"))
                            writeMoveCalls++;
                        if(owner.equals("java/nio/file/Files")
                                && methodName.equals("deleteIfExists"))
                            writeDeletesTempOnFailure = true;
                    }
                };
            }

            return null;
        }
    }

    private static final class OptionsVisitor extends ClassVisitor {
        boolean applySavesMinecraftOptions;

        OptionsVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"applyOptions".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/minecraft/client/Options")
                            && methodName.equals("save")
                            && methodDescriptor.equals("()V"))
                        applySavesMinecraftOptions = true;
                }
            };
        }
    }
}

import java.nio.file.*;
import java.util.Arrays;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve the pinned Audi address-book implementation; seed each new consumer. */
public final class AddressBookPatch {
    static final String OWNER = "de/audi/app/addressbook/core/combi/bap/CombiBAPServiceHandler";
    static final String HANDLER = "Lde/audi/app/addressbook/core/combi/bap/CombiADBHandler;";
    static final String SERVICE = "Lde/audi/atip/interapp/combi/bap/phone/CombiBAPServiceAddressBook;";
    static final String HOOK = "com/luka/carplay/telephony/AddressBookRefresh";

    static byte[] patch(byte[] input) throws Exception {
        TransportDevicePatch.require(TransportDevicePatch.sha(input).equals(
            "798ad5d7613554e83cf98c6511498061e65681f6cdf1adf0a35eac78b999a709"),
            "Unrecognized stock address-book service");
        ClassNode c = new ClassNode();
        new ClassReader(input).accept(c, 0);
        TransportDevicePatch.require(c.name.equals(OWNER), "Wrong address-book class");
        c.version = Opcodes.V1_4;
        int edits = 0;
        for (MethodNode m : c.methods) {
            if (!m.name.equals("setCombiService") || !m.desc.equals("(" + SERVICE + ")V")) continue;
            for (AbstractInsnNode n : m.instructions.toArray()) {
                if (n.getOpcode() != Opcodes.RETURN) continue;
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "adbHandler", HANDLER));
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "bind",
                    "(" + HANDLER + SERVICE + ")V", false));
                m.instructions.insertBefore(n, code);
                edits++;
            }
        }
        TransportDevicePatch.require(edits == 1, "Unexpected address-book binding exits");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(writer);
        byte[] output = writer.toByteArray();
        ClassNode original = new ClassNode(), stripped = new ClassNode();
        new ClassReader(input).accept(original, 0);
        new ClassReader(output).accept(stripped, 0);
        int removed = 0;
        for (MethodNode m : stripped.methods) {
            for (AbstractInsnNode n : m.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode) || !((MethodInsnNode)n).owner.equals(HOOK)) continue;
                for (int i = 0; i < 4; i++) {
                    AbstractInsnNode previous = n.getPrevious();
                    m.instructions.remove(n); n = previous;
                }
                removed++;
            }
            for (MethodNode old : original.methods)
                if (old.name.equals(m.name) && old.desc.equals(m.desc)) m.maxStack = old.maxStack;
        }
        stripped.version = original.version;
        TransportDevicePatch.require(removed == 1 && Arrays.equals(
            TransportDevicePatch.canonical(original), TransportDevicePatch.canonical(stripped)),
            "Stock address-book bytecode changed outside the binding insert");
        return output;
    }

    public static void main(String[] args) throws Exception {
        TransportDevicePatch.require(args.length == 2, "usage: AddressBookPatch stock.jar output-classes");
        try (JarFile jar = new JarFile(args[0])) {
            byte[] output = patch(TransportDevicePatch.read(jar, OWNER));
            Path path = Paths.get(args[1], OWNER + ".class");
            Files.createDirectories(path.getParent());
            Files.write(path, output);
            System.out.println("Address-book binding hook: sha256=" + TransportDevicePatch.sha(output));
        }
    }
}

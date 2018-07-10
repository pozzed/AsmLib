package net.futureclient.asm.internal;

import com.google.common.collect.Streams;
import net.futureclient.asm.config.ConfigManager;
import net.futureclient.asm.config.Config;
import net.futureclient.asm.transformer.annotation.Inject;
import net.futureclient.asm.transformer.annotation.Transformer;
import net.futureclient.asm.transformer.util.AnnotationInfo;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;


/**
 * Created by Babbaj on 5/20/2018.
 * <p>
 * This class is responsible for processing the bytecode of transformers
 */
public final class TransformerPreProcessor implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (configContainsClass(transformedName)) {
            ClassNode cn = new ClassNode();
            ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            if (!hasAnnotation(cn, Transformer.class)) {
                AsmLib.LOGGER.error("Transformer Class {} is missing @{} annotation", transformedName, Transformer.class.getSimpleName());
                return basicClass;
            }

            processClass(cn);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return basicClass;
    }

    private boolean configContainsClass(String className) {
        return ConfigManager.INSTANCE
                .getConfigs().stream()
                .map(Config::getTransformerClassNames)
                .flatMap(Collection::stream)
                .anyMatch(className::equals);
    }


    private boolean hasAnnotation(ClassNode cn, Class<? extends Annotation> clazz) {
        return cn.visibleAnnotations.stream()
                .anyMatch(node -> node.desc.equals(Type.getDescriptor(clazz)));
    }

    // TODO: make sure we have java lambdas
    private void processLambdas(MethodNode method) {
        Streams.stream(method.instructions.iterator())
                .filter(InvokeDynamicInsnNode.class::isInstance)
                .map(InvokeDynamicInsnNode.class::cast)
                .forEach(node -> {
                    injectAtLambda(method, node);
                });
    }
    private void injectAtLambda(MethodNode method, InvokeDynamicInsnNode node) {
        Handle methodRef = (Handle)node.bsmArgs[1];
        Type realType = (Type)node.bsmArgs[2]; // MethodType

        InsnList list = new InsnList();
        list.add(new InsnNode(DUP));
        // method reference
        list.add(new LdcInsnNode(methodRef.getOwner()));
        list.add(new LdcInsnNode(methodRef.getName()));
        list.add(new LdcInsnNode(methodRef.getDesc()));
        list.add(new LdcInsnNode(methodRef.getTag()));
        // instantiatedMethodType
        list.add(new LdcInsnNode(realType.getInternalName()));
        // InvokeDynamicInsnNode desc
        list.add(new LdcInsnNode(node.desc));
        list.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(LambdaInfo.class), "addLambda",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V"));

        method.instructions.insert(node, list);
    }

    private void processClass(ClassNode clazz) {
        clazz.methods.forEach(node -> processLambdas(node));

        // TODO: only apply to lambda methods
        clazz.methods.stream()
                .filter(method -> (method.access & ACC_SYNTHETIC) != 0)
                .forEach(method -> {
                    method.access &= ~ACC_PRIVATE;
                    method.access |= ACC_PUBLIC;
                });

        clazz.visibleAnnotations.stream()
                .filter(node -> node.desc.equals(Type.getDescriptor(Transformer.class)))
                .findFirst()
                .ifPresent(this::processTransformer);

        clazz.methods.stream()
                .map(node -> node.visibleAnnotations)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(node -> node.desc.equals(Type.getDescriptor(Inject.class)))
                .forEach(this::processInject);

        // TODO: create delegate here
        final Class<? extends TransformerDelegate> wrapperClass =
                TransformerDelegate.createDelegateClass(clazz, Type.getObjectType(clazz.name));
        TransformerDelegate.DELEGATES.put(clazz.name, wrapperClass);
    }

    // works for primitives
    private String getInternalName(Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
            return type.getInternalName();
        return type.getDescriptor();
    }

    // process @Transformer annotation
    @SuppressWarnings("unchecked")
    private void processTransformer(AnnotationNode node) {
        AnnotationInfo info = AnnotationInfo.fromAsm(node);
        if (info.getValue("target") == null) {
            node.values.add(0, "target");
            String newTarget = Optional.of(info.<Type>getValue("value"))
                    .map(Type::getClassName)
                    .get();
            node.values.add(1, newTarget);
        }
        final int i = node.values.indexOf("value");
        if (i != -1) {
            node.values.remove(i + 1);
            node.values.remove(i);
        }
    }

    // process @Inject annotation
    private void processInject(final AnnotationNode node) {
        AnnotationInfo info = AnnotationInfo.fromAsm(node);
        if (info.getValue("target") == null) {
            final String name = info.getValue("name");
            if (name == null)
                throw new IllegalArgumentException("Failed to supply method name");
            final String ret = Optional.ofNullable(info.<Type>getValue("ret")).orElse(Type.VOID_TYPE).getDescriptor();
            final String args = Optional.ofNullable(info.<List<Type>>getValue("args"))
                    .map(list -> list.stream()
                            .map(Type::getDescriptor)
                            .collect(Collectors.joining()))
                    .orElse("");

            final String fullDesc = String.format("%s(%s)%s", name, args, ret);
            int i = node.values.indexOf("target");
            if (i == -1) {
                node.values.add(0, "target");
                node.values.add(1, fullDesc);
            } else {
                node.values.set(i + 1, fullDesc);
            }
        }
        // target value is set, remove the old annotation values because theyre unnecessary and cause problems
        String[] oldValueNames = {"name", "args", "ret"};
        for (String valueName : oldValueNames) {
            final int index = node.values.indexOf(valueName);
            if (index != -1) {
                node.values.remove(index + 1);
                node.values.remove(index);
            }
        }
    }
}

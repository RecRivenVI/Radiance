package com.radiance.render.contract;

import java.util.List;
import java.util.Objects;

/** Immutable source identities shared by persistent model and section submissions. */
public final class TakeoverSubmission {
    private TakeoverSubmission() {}

    public enum Domain {
        WORLD,
        GUI,
        SCREEN
    }

    public enum Change {
        TRANSFORM,
        ATTRIBUTES,
        MATERIAL,
        TOPOLOGY,
        REMOVE
    }

    public enum Executor {
        CAPTURE,
        DIRECT
    }

    public record Resource(
            long epoch, long identity, long topology, long attributes, long material) {
        public Resource {
            if (epoch < 0 || identity <= 0 || topology <= 0 || attributes <= 0 || material <= 0)
                throw new IllegalArgumentException("Invalid source resource version");
        }
    }

    public record Instance(long owner, int part, Resource resource) {
        public Instance {
            Objects.requireNonNull(resource);
            if (owner < 0 || part < 0)
                throw new IllegalArgumentException("Invalid source instance");
        }
    }

    public record Update(Resource before, Resource after, Change change) {
        public Update {
            Objects.requireNonNull(change);
            if (before == null && after == null) throw new IllegalArgumentException("Empty update");
            if (change == Change.REMOVE && after != null)
                throw new IllegalArgumentException("Live removal");
            if (before != null && after != null && before.epoch != after.epoch)
                throw new IllegalArgumentException("Update crosses a source epoch");
            if (change == Change.TRANSFORM && !Objects.equals(before, after))
                throw new IllegalArgumentException("Transform changed the resource");
        }
    }

    public record Dependency(long pass, long attachment, long revision) {
        public Dependency {
            if (pass < 0 || attachment < 0 || revision <= 0)
                throw new IllegalArgumentException("Invalid dependency");
        }
    }

    /** Adapter-owned program identity; mutable game shader/RenderType objects stay outside. */
    public record Program(long identity, long revision) {
        public Program {
            if (identity <= 0 || revision <= 0)
                throw new IllegalArgumentException("Invalid source program version");
        }
    }

    public enum LayerKind {
        EYES,
        ENERGY,
        SURFACE_COATING,
        DECORATION
    }

    public enum Composition {
        EYE_OWNER,
        SURFACE_OWNER,
        SOURCE_ORDER
    }

    /** Source order is retained; this describes execution, it never reorders source callbacks. */
    public record Layer(int order, LayerKind kind, Composition composition) {
        public Layer {
            if (order < 0) throw new IllegalArgumentException("Negative layer order");
            Objects.requireNonNull(kind);
            Objects.requireNonNull(composition);
        }
    }

    public record LayerPlan(List<Layer> layers) {
        public LayerPlan {
            layers = List.copyOf(layers);
            int previous = -1;
            for (var layer : layers) {
                if (layer.order <= previous) throw new IllegalArgumentException("Layer order");
                previous = layer.order;
            }
        }

        public boolean eyeOwner() {
            return layers.stream().anyMatch(l -> l.composition == Composition.EYE_OWNER);
        }

        public boolean surfaceOwner() {
            return !eyeOwner()
                    && layers.stream().anyMatch(l -> l.composition == Composition.SURFACE_OWNER);
        }
    }

    public record Draw(
            Instance instance,
            Domain domain,
            Executor executor,
            Program program,
            DrawStateSnapshot state,
            List<Dependency> dependencies) {
        public Draw {
            Objects.requireNonNull(instance);
            Objects.requireNonNull(domain);
            Objects.requireNonNull(executor);
            Objects.requireNonNull(program);
            Objects.requireNonNull(state);
            dependencies = List.copyOf(dependencies);
        }
    }

    /** A completed version may replace the published one only while it is still desired. */
    public record Publication(Resource desired, Resource completed, Resource published) {
        public boolean mayPublish() {
            return desired != null && desired.equals(completed);
        }

        public Resource visible() {
            return mayPublish() ? completed : published;
        }
    }

    /** One production request has one executor, even when callbacks recurse. */
    public static final class Ownership implements AutoCloseable {
        private final Ownership parent;
        private Executor executor;
        private boolean closed;
        private static final ThreadLocal<Ownership> CURRENT = new ThreadLocal<>();

        private Ownership() {
            parent = CURRENT.get();
            CURRENT.set(this);
        }

        public static Ownership open() {
            return new Ownership();
        }

        public void claim(Executor owner) {
            if (closed || CURRENT.get() != this || executor != null)
                throw new IllegalStateException("A source request already has an executor");
            executor = Objects.requireNonNull(owner);
        }

        public Executor executor() {
            return executor;
        }

        @Override
        public void close() {
            if (closed) return;
            if (CURRENT.get() != this) throw new IllegalStateException("Source scope order");
            closed = true;
            if (parent == null) CURRENT.remove();
            else CURRENT.set(parent);
        }
    }
}

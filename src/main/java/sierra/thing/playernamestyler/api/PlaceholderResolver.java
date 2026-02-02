package sierra.thing.playernamestyler.api;

@FunctionalInterface
public interface PlaceholderResolver {
    String resolve(PlaceholderContext ctx);
}


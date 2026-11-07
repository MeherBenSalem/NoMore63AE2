package dev.nightbeam.corerpg.quests.schema;

/** What a resource-location property points at, so the editor can offer the right suggestions. */
public enum ResourceKind {
    ANY(false),
    ITEM(false),
    BLOCK(false),
    FLUID(false),
    ENTITY(false),
    BIOME(false),
    DIMENSION(false),
    STRUCTURE(false),
    ADVANCEMENT(false),
    STAT(false),
    LOOT_TABLE(false),
    SOUND(false),
    TEXTURE(false),
    ITEM_TAG(true),
    BLOCK_TAG(true),
    ENTITY_TAG(true),
    BIOME_TAG(true),
    STRUCTURE_TAG(true);

    public final boolean tag;

    ResourceKind(boolean tag) {
        this.tag = tag;
    }
}

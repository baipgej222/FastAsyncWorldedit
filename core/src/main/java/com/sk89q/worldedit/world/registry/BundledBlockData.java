/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.world.registry;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockMaterial;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Provides block data based on the built-in block database that is bundled
 * with WorldEdit.
 *
 * <p>A new instance cannot be created. Use {@link #getInstance()} to get
 * an instance.</p>
 *
 * <p>The data is read from a JSON file that is bundled with WorldEdit. If
 * reading fails (which occurs when this class is first instantiated), then
 * the methods will return {@code null}s for all blocks.</p>
 */
public class BundledBlockData {

    private static final Logger log = Logger.getLogger(BundledBlockData.class.getCanonicalName());
    private static final BundledBlockData INSTANCE = new BundledBlockData();

    private final Map<String, BlockEntry> idMap = new HashMap<String, BlockEntry>();
    private final Map<Integer, BlockEntry> legacyMap = new HashMap<Integer, BlockEntry>(); // Trove usage removed temporarily

    /**
     * Create a new instance.
     */
    private BundledBlockData() {

    }

    /**
     * Attempt to load the data from file.
     *
     * @throws IOException thrown on I/O error
     */
    public void loadFromResource() throws IOException {
        URL url = WorldEdit.getInstance().getClass().getResource("/com/sk89q/worldedit/world/registry/blocks.json");
        add(url, false);
    }

    public void add(URL url, boolean overwrite) throws IOException {
        if (url == null) {
            throw new IOException("Could not find " + url);
        }
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Vector.class, new FaweVectorAdapter());
        Gson gson = gsonBuilder.create();
        String data = Resources.toString(url, Charset.defaultCharset());
        List<BlockEntry> entries = gson.fromJson(data, new TypeToken<List<BlockEntry>>() {}.getType());
        for (BlockEntry entry : entries) {
            add(entry, overwrite);
        }
    }

    public boolean add(BlockEntry entry, boolean overwrite) {
        entry.postDeserialization();
        if (!overwrite && (idMap.containsKey(entry.id) || legacyMap.containsKey(entry.legacyId))) {
            return false;
        }
        idMap.put(entry.id, entry);
        legacyMap.put(entry.legacyId, entry);
        return true;
    }

    /**
     * Return the entry for the given block ID.
     *
     * @param id the ID
     * @return the entry, or null
     */
    @Nullable
    public BlockEntry findById(String id) {
        return idMap.get(id);
    }

    /**
     * Return the entry for the given block legacy numeric ID.
     *
     * @param id the ID
     * @return the entry, or null
     */
    @Nullable
    public BlockEntry findById(int id) {
        return legacyMap.get(id);
    }

    /**
     * Convert the given string ID to a legacy numeric ID.
     *
     * @param id the ID
     * @return the legacy ID, which may be null if the block does not have a legacy ID
     */
    @Nullable
    public Integer toLegacyId(String id) {
        BlockEntry entry = findById(id);
        if (entry != null) {
            return entry.legacyId;
        } else {
            return null;
        }
    }

    /**
     * Get the material properties for the given block.
     *
     * @param id the legacy numeric ID
     * @return the material's properties, or null
     */
    @Nullable
    public BlockMaterial getMaterialById(int id) {
        BlockEntry entry = findById(id);
        if (entry != null) {
            return entry.material;
        } else {
            return null;
        }
    }

    /**
     * Get the states for the given block.
     *
     * @param id the legacy numeric ID
     * @return the block's states, or null if no information is available
     */
    @Nullable
    public Map<String, ? extends State> getStatesById(int id) {
        BlockEntry entry = findById(id);
        if (entry != null) {
            return entry.states;
        } else {
            return null;
        }
    }

    /**
     * Get a singleton instance of this object.
     *
     * @return the instance
     */
    public static BundledBlockData getInstance() {
        return INSTANCE;
    }

    public static class BlockEntry {
        private int legacyId;
        private String id;
        private String unlocalizedName;
        private List<String> aliases;
        private Map<String, FaweState> states = new HashMap<String, FaweState>();
        private FaweBlockMaterial material = new FaweBlockMaterial();

        void postDeserialization() {
            for (FaweState state : states.values()) {
                state.postDeserialization();
            }
        }
    }

    public static Class<?> inject() {
        return BundledBlockData.class;
    }

    public class FaweVectorAdapter implements JsonDeserializer<Vector> {

        @Override
        public Vector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonArray jsonArray = json.getAsJsonArray();
            if (jsonArray.size() != 3) {
                throw new JsonParseException("Expected array of 3 length for Vector");
            }

            double x = jsonArray.get(0).getAsDouble();
            double y = jsonArray.get(1).getAsDouble();
            double z = jsonArray.get(2).getAsDouble();

            return new Vector(x, y, z);
        }
    }

    class FaweStateValue implements StateValue {

        private FaweState state;
        private Byte data;
        private Vector direction;

        void setState(FaweState state) {
            this.state = state;
        }

        @Override
        public boolean isSet(BaseBlock block) {
            return data != null && (block.getData() & state.getDataMask()) == data;
        }

        @Override
        public boolean set(BaseBlock block) {
            if (data != null) {
                block.setData((block.getData() & ~state.getDataMask()) | data);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Vector getDirection() {
            return direction;
        }

    }

    class FaweState implements State {

        private Byte dataMask;
        private Map<String, FaweStateValue> values;

        @Override
        public Map<String, FaweStateValue> valueMap() {
            return Collections.unmodifiableMap(values);
        }

        @Nullable
        @Override
        public StateValue getValue(BaseBlock block) {
            for (StateValue value : values.values()) {
                if (value.isSet(block)) {
                    return value;
                }
            }

            return null;
        }

        byte getDataMask() {
            return dataMask != null ? dataMask : 0xF;
        }

        @Override
        public boolean hasDirection() {
            for (FaweStateValue value : values.values()) {
                if (value.getDirection() != null) {
                    return true;
                }
            }

            return false;
        }

        void postDeserialization() {
            for (FaweStateValue v : values.values()) {
                v.setState(this);
            }
        }

    }

    public static class FaweBlockMaterial implements BlockMaterial {

        private boolean renderedAsNormalBlock;
        private boolean fullCube;
        private boolean opaque;
        private boolean powerSource;
        private boolean liquid;
        private boolean solid;
        private float hardness;
        private float resistance;
        private float slipperiness;
        private boolean grassBlocking;
        private float ambientOcclusionLightValue;
        private int lightOpacity;
        private int lightValue;
        private boolean fragileWhenPushed;
        private boolean unpushable;
        private boolean adventureModeExempt;
        private boolean ticksRandomly;
        private boolean usingNeighborLight;
        private boolean movementBlocker;
        private boolean burnable;
        private boolean toolRequired;
        private boolean replacedDuringPlacement;

        @Override
        public boolean isRenderedAsNormalBlock() {
            return renderedAsNormalBlock;
        }

        public void setRenderedAsNormalBlock(boolean renderedAsNormalBlock) {
            this.renderedAsNormalBlock = renderedAsNormalBlock;
        }

        @Override
        public boolean isFullCube() {
            return fullCube;
        }

        public void setFullCube(boolean fullCube) {
            this.fullCube = fullCube;
        }

        @Override
        public boolean isOpaque() {
            return opaque;
        }

        public void setOpaque(boolean opaque) {
            this.opaque = opaque;
        }

        @Override
        public boolean isPowerSource() {
            return powerSource;
        }

        public void setPowerSource(boolean powerSource) {
            this.powerSource = powerSource;
        }

        @Override
        public boolean isLiquid() {
            return liquid;
        }

        public void setLiquid(boolean liquid) {
            this.liquid = liquid;
        }

        @Override
        public boolean isSolid() {
            return solid;
        }

        public void setSolid(boolean solid) {
            this.solid = solid;
        }

        @Override
        public float getHardness() {
            return hardness;
        }

        public void setHardness(float hardness) {
            this.hardness = hardness;
        }

        @Override
        public float getResistance() {
            return resistance;
        }

        public void setResistance(float resistance) {
            this.resistance = resistance;
        }

        @Override
        public float getSlipperiness() {
            return slipperiness;
        }

        public void setSlipperiness(float slipperiness) {
            this.slipperiness = slipperiness;
        }

        @Override
        public boolean isGrassBlocking() {
            return grassBlocking;
        }

        public void setGrassBlocking(boolean grassBlocking) {
            this.grassBlocking = grassBlocking;
        }

        @Override
        public float getAmbientOcclusionLightValue() {
            return ambientOcclusionLightValue;
        }

        public void setAmbientOcclusionLightValue(float ambientOcclusionLightValue) {
            this.ambientOcclusionLightValue = ambientOcclusionLightValue;
        }

        @Override
        public int getLightOpacity() {
            return lightOpacity;
        }

        public void setLightOpacity(int lightOpacity) {
            this.lightOpacity = lightOpacity;
        }

        @Override
        public int getLightValue() {
            return lightValue;
        }

        public void setLightValue(int lightValue) {
            this.lightValue = lightValue;
        }

        @Override
        public boolean isFragileWhenPushed() {
            return fragileWhenPushed;
        }

        public void setFragileWhenPushed(boolean fragileWhenPushed) {
            this.fragileWhenPushed = fragileWhenPushed;
        }

        @Override
        public boolean isUnpushable() {
            return unpushable;
        }

        public void setUnpushable(boolean unpushable) {
            this.unpushable = unpushable;
        }

        @Override
        public boolean isAdventureModeExempt() {
            return adventureModeExempt;
        }

        public void setAdventureModeExempt(boolean adventureModeExempt) {
            this.adventureModeExempt = adventureModeExempt;
        }

        @Override
        public boolean isTicksRandomly() {
            return ticksRandomly;
        }

        public void setTicksRandomly(boolean ticksRandomly) {
            this.ticksRandomly = ticksRandomly;
        }

        @Override
        public boolean isUsingNeighborLight() {
            return usingNeighborLight;
        }

        public void setUsingNeighborLight(boolean usingNeighborLight) {
            this.usingNeighborLight = usingNeighborLight;
        }

        @Override
        public boolean isMovementBlocker() {
            return movementBlocker;
        }

        public void setMovementBlocker(boolean movementBlocker) {
            this.movementBlocker = movementBlocker;
        }

        @Override
        public boolean isBurnable() {
            return burnable;
        }

        public void setBurnable(boolean burnable) {
            this.burnable = burnable;
        }

        @Override
        public boolean isToolRequired() {
            return toolRequired;
        }

        public void setToolRequired(boolean toolRequired) {
            this.toolRequired = toolRequired;
        }

        @Override
        public boolean isReplacedDuringPlacement() {
            return replacedDuringPlacement;
        }

        public void setReplacedDuringPlacement(boolean replacedDuringPlacement) {
            this.replacedDuringPlacement = replacedDuringPlacement;
        }
    }
}
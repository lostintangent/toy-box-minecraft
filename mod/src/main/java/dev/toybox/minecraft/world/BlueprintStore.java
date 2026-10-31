package dev.toybox.minecraft.world;

import dev.toybox.minecraft.ToyBoxMinecraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Remembers the blueprint a builder applied last ({@code toybox_minecraft-hq-plan.dat} in the world
 * folder: builder id, box, a block-state palette and one palette index per cell). The next build
 * compares the world against it to tell the player's own changes (kept) from cells that simply
 * still hold an older version of the building (updated).
 */
final class BlueprintStore {
  // Keep the existing filename so rebuilds can recognize and preserve player edits.
  static final String FILE = "toybox_minecraft-hq-plan.dat";

  private BlueprintStore() {}

  private static Path file(MinecraftServer server) {
    return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
  }

  /** The previous blueprint of {@code builder} for exactly this box, or null. */
  static BlockState @Nullable [] load(
      MinecraftServer server, String builder, int[] box, int cells) {
    Path f = file(server);
    if (!Files.exists(f)) {
      return null;
    }
    try {
      CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
      if (!builder.equals(root.getStringOr("builder", ""))
          || !java.util.Arrays.equals(box, root.getIntArray("box").orElse(new int[0]))) {
        return null;
      }
      ListTag palette = root.getListOrEmpty("palette");
      BlockState[] states = new BlockState[palette.size()];
      for (int i = 0; i < states.length; i++) {
        states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i));
      }
      int[] idx = root.getIntArray("cells").orElse(new int[0]);
      if (idx.length != cells) {
        return null;
      }
      BlockState[] out = new BlockState[cells];
      for (int i = 0; i < cells; i++) {
        out[i] = states[idx[i]];
      }
      return out;
    } catch (IOException | RuntimeException e) {
      ToyBoxMinecraft.LOGGER.warn("Could not read {} (the next rebuild sets every cell)", f, e);
      return null;
    }
  }

  static void save(MinecraftServer server, String builder, int[] box, BlockState[] cells) {
    Map<BlockState, Integer> ids = new HashMap<>();
    ListTag palette = new ListTag();
    int[] idx = new int[cells.length];
    for (int i = 0; i < cells.length; i++) {
      Integer id = ids.get(cells[i]);
      if (id == null) {
        id = ids.size();
        ids.put(cells[i], id);
        palette.add(NbtUtils.writeBlockState(cells[i]));
      }
      idx[i] = id;
    }
    CompoundTag root = new CompoundTag();
    root.putString("builder", builder);
    root.putIntArray("box", box);
    root.put("palette", palette);
    root.put("cells", new IntArrayTag(idx));
    Path f = file(server);
    try {
      Path tmp = f.resolveSibling(FILE + ".tmp");
      NbtIo.writeCompressed(root, tmp);
      Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      ToyBoxMinecraft.LOGGER.warn("Could not save {}", f, e);
    }
  }

  static boolean exists(MinecraftServer server) {
    return Files.exists(file(server));
  }
}

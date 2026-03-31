package com.swarmvshunter;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class SwarmVsHunterTest {

    private ServerMock server;
    private SwarmVsHunter plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SwarmVsHunter.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void svhStart_generatesFieldAndChangesState() {
        player.performCommand("svh start");

        assertEquals(SwarmVsHunter.GameState.PLAYING, plugin.gameState,
                "gameState should be PLAYING after /svh start");
        assertNotNull(plugin.fieldOrigin, "fieldOrigin should be set");
    }

    @Test
    void svhStart_secondTimeShowsAlreadyPlaying() {
        player.performCommand("svh start");
        player.performCommand("svh start");

        String lastMsg = player.nextMessage();
        // skip first messages, get to the "already playing" message
        String msg;
        String lastReceived = lastMsg;
        while ((msg = player.nextMessage()) != null) {
            lastReceived = msg;
        }
        assertTrue(lastReceived.contains("ゲームが既に進行中です"),
                "Should show 'already playing' message on second start");
    }

    @Test
    void svhStart_fieldSizeIs70x70() {
        assertEquals(70, plugin.fieldSize, "Field size should be 70");
    }

    @Test
    void svhStart_outerWallsAreStoneBricks() {
        player.performCommand("svh start");

        int ox = plugin.fieldOrigin.getBlockX();
        int baseY = plugin.fieldOrigin.getBlockY();
        int oz = plugin.fieldOrigin.getBlockZ();
        int size = plugin.fieldSize;

        // Check north wall (z=0)
        Block wallBlock = plugin.fieldOrigin.getWorld().getBlockAt(ox + 5, baseY + 1, oz);
        assertEquals(Material.STONE_BRICKS, wallBlock.getType(),
                "Outer wall should be stone bricks");

        // Check south wall (z=size-1)
        Block southWall = plugin.fieldOrigin.getWorld().getBlockAt(ox + 5, baseY + 1, oz + size - 1);
        assertEquals(Material.STONE_BRICKS, southWall.getType(),
                "South wall should be stone bricks");

        // Check west wall (x=0)
        Block westWall = plugin.fieldOrigin.getWorld().getBlockAt(ox, baseY + 1, oz + 5);
        assertEquals(Material.STONE_BRICKS, westWall.getType(),
                "West wall should be stone bricks");

        // Check east wall (x=size-1)
        Block eastWall = plugin.fieldOrigin.getWorld().getBlockAt(ox + size - 1, baseY + 1, oz + 5);
        assertEquals(Material.STONE_BRICKS, eastWall.getType(),
                "East wall should be stone bricks");
    }

    @Test
    void svhStart_playerTeleportedToFieldCenter() {
        player.performCommand("svh start");

        double expectedX = plugin.fieldOrigin.getBlockX() + plugin.fieldSize / 2.0;
        double expectedZ = plugin.fieldOrigin.getBlockZ() + plugin.fieldSize / 2.0;

        assertEquals(expectedX, player.getLocation().getX(), 1.0,
                "Player should be teleported to field center X");
        assertEquals(expectedZ, player.getLocation().getZ(), 1.0,
                "Player should be teleported to field center Z");
    }

    @Test
    void svhCommand_nonPlayerRejected() {
        // ConsoleCommandSender executing the command
        server.executeConsole("svh", "start");
        // No crash = pass; console should get rejection message
    }
}

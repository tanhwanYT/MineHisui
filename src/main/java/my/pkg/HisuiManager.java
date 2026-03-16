package my.pkg;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HisuiManager implements Listener, CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final NamespacedKey skillKey;

    // 플레이어별 상태 저장
    private final Map<UUID, HisuiState> stateMap = new ConcurrentHashMap<>();

    // 기본 쿨타임(ms) - 임시값
    private static final long Q_COOLDOWN = 7000L;
    private static final long W_COOLDOWN = 12000L;
    private static final long E_COOLDOWN = 9000L;
    private static final long R_COOLDOWN = 70000L;

    private final my.pkg.HisuiQSkill qSkill;
    private final my.pkg.HisuiESkill eSkill;

    public HisuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.skillKey = new NamespacedKey(plugin, "hisui_skill");
        this.qSkill = new my.pkg.HisuiQSkill(plugin, this);
        this.eSkill = new my.pkg.HisuiESkill(plugin, this);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (plugin.getCommand("hisui") != null) {
            plugin.getCommand("hisui").setExecutor(this);
            plugin.getCommand("hisui").setTabCompleter(this);
        }
    }

    // =========================
    // 명령어
    // =========================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("플레이어만 사용할 수 있습니다.");
                return true;
            }

            giveSkillItems(player);
            sender.sendMessage(ChatColor.GREEN + "[히스이] 스킬 아이템이 지급되었습니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "OP만 사용할 수 있습니다.");
                return true;
            }

            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다.");
                    return true;
                }

                giveSkillItems(target);
                sender.sendMessage(ChatColor.GREEN + "[히스이] " + target.getName() + " 에게 스킬 아이템 지급 완료.");
                target.sendMessage(ChatColor.GREEN + "[히스이] 스킬 아이템을 지급받았습니다.");
                return true;
            }

            if (sender instanceof Player player) {
                giveSkillItems(player);
                sender.sendMessage(ChatColor.GREEN + "[히스이] 스킬 아이템이 지급되었습니다.");
            } else {
                sender.sendMessage("/hisui give <player>");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "OP만 사용할 수 있습니다.");
                return true;
            }

            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다.");
                    return true;
                }

                stateMap.remove(target.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + "[히스이] " + target.getName() + " 상태 초기화 완료.");
                return true;
            }

            if (sender instanceof Player player) {
                stateMap.remove(player.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + "[히스이] 상태가 초기화되었습니다.");
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "사용법: /hisui 또는 /hisui give [player] 또는 /hisui reset [player]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "reset");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return list;
        }

        return Collections.emptyList();
    }

    // =========================
    // 이벤트
    // =========================
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        String skillId = getSkillId(item);
        if (skillId == null) return;

        event.setCancelled(true);

        switch (skillId.toLowerCase()) {
            case "q" -> useQ(player);
            case "w" -> useW(player);
            case "e" -> useE(player);
            case "r" -> useR(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        String skillId = getSkillId(item);
        if (skillId == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "히스이 스킬 아이템은 버릴 수 없습니다.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 필요 시 나가면 상태 정리
        // stateMap.remove(event.getPlayer().getUniqueId());
    }

    // =========================
    // 아이템 지급
    // =========================
    public void giveSkillItems(Player player) {
        PlayerInventory inv = player.getInventory();

        inv.setItem(0, createSkillItem("q", ChatColor.AQUA + "히스이 Q - 쾌연격", Material.IRON_SWORD,
                Arrays.asList(
                        ChatColor.GRAY + "우클릭으로 사용",
                        ChatColor.DARK_GRAY + "원형 1타 + 전방 1타"
                )));

        inv.setItem(1, createSkillItem("w", ChatColor.GOLD + "히스이 W - 거합", Material.GOLDEN_SWORD,
                Arrays.asList(
                        ChatColor.GRAY + "우클릭으로 사용",
                        ChatColor.DARK_GRAY + "거합 중첩에 따라 스킬 변화"
                )));

        inv.setItem(2, createSkillItem("e", ChatColor.GREEN + "히스이 E - 회피/돌진", Material.STONE_SWORD,
                Arrays.asList(
                        ChatColor.GRAY + "우클릭으로 사용",
                        ChatColor.DARK_GRAY + "뒤로 빠졌다가 앞으로 돌진"
                )));

        inv.setItem(3, createSkillItem("r", ChatColor.LIGHT_PURPLE + "히스이 R - 모노호시자오", Material.NETHERITE_SWORD,
                Arrays.asList(
                        ChatColor.GRAY + "우클릭으로 사용",
                        ChatColor.DARK_GRAY + "강화 상태 진입 / 재사용 가능"
                )));

        player.sendMessage(ChatColor.GREEN + "[히스이] Q/W/E/R 스킬 아이템이 지급되었습니다.");
    }

    private ItemStack createSkillItem(String skillId, String displayName, Material material, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, skillId);

        item.setItemMeta(meta);
        return item;
    }

    private String getSkillId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(skillKey, PersistentDataType.STRING);
    }

    // =========================
    // 상태
    // =========================
    private HisuiState getState(Player player) {
        return stateMap.computeIfAbsent(player.getUniqueId(), uuid -> new HisuiState());
    }

    private boolean isOnCooldown(long cooldownEnd) {
        return System.currentTimeMillis() < cooldownEnd;
    }

    private String formatCooldown(long cooldownEnd) {
        long remain = cooldownEnd - System.currentTimeMillis();
        if (remain < 0) remain = 0;
        return String.format(Locale.US, "%.1f", remain / 1000.0);
    }

    // =========================
    // 스킬 사용
    // =========================
    private void useQ(Player player) {
        HisuiState state = getState(player);

        if (isOnCooldown(state.qCooldownEnd)) {
            player.sendMessage(ChatColor.RED + "[Q] 쿨타임: " + formatCooldown(state.qCooldownEnd) + "초");
            return;
        }

        state.qCooldownEnd = System.currentTimeMillis() + Q_COOLDOWN;
        grantMemory(state);

        player.sendMessage(ChatColor.AQUA + "[히스이] Q - 쾌연격!");
        qSkill.cast(player);
    }

    private void useW(Player player) {
        HisuiState state = getState(player);

        if (state.iaiStack <= 0) {
            player.sendMessage(ChatColor.RED + "[W] 거합 중첩이 없어 사용할 수 없습니다.");
            return;
        }

        if (isOnCooldown(state.wCooldownEnd)) {
            player.sendMessage(ChatColor.RED + "[W] 쿨타임: " + formatCooldown(state.wCooldownEnd) + "초");
            return;
        }

        state.wCooldownEnd = System.currentTimeMillis() + W_COOLDOWN;
        grantMemory(state);

        int currentIai = state.iaiStack;
        state.iaiStack = 0;

        if (currentIai == 1) {
            player.sendMessage(ChatColor.GOLD + "[히스이] W1 - 일도양단 사용!");
        } else if (currentIai == 2) {
            player.sendMessage(ChatColor.GOLD + "[히스이] W2 - 일섬 사용!");
        } else {
            player.sendMessage(ChatColor.GOLD + "[히스이] W3 - 일륜난무 사용!");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);

        // TODO: 실제 W 스킬 로직 연결
        // 예: wSkill.cast(player, currentIai);
    }

    private void useE(Player player) {
        HisuiState state = getState(player);

        if (isOnCooldown(state.eCooldownEnd)) {
            player.sendMessage(ChatColor.RED + "[E] 쿨타임: " + formatCooldown(state.eCooldownEnd) + "초");
            return;
        }

        state.eCooldownEnd = System.currentTimeMillis() + E_COOLDOWN;
        grantMemory(state);

        player.sendMessage(ChatColor.GREEN + "[히스이] E - 회피/돌진!");
        eSkill.cast(player);
    }

    private void useR(Player player) {
        HisuiState state = getState(player);

        // 이미 R 활성화 상태면 재사용 처리
        if (state.rActive && System.currentTimeMillis() < state.rExpireAt) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[히스이] R 재사용 - 베기 발동!");
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1.2f);

            state.rActive = false;
            state.rExpireAt = 0L;

            // TODO: 실제 R2 스킬 로직 연결
            // 예: rSkill.castSecond(player);
            return;
        }

        if (isOnCooldown(state.rCooldownEnd)) {
            player.sendMessage(ChatColor.RED + "[R] 쿨타임: " + formatCooldown(state.rCooldownEnd) + "초");
            return;
        }

        state.rCooldownEnd = System.currentTimeMillis() + R_COOLDOWN;
        state.rActive = true;
        state.rExpireAt = System.currentTimeMillis() + 15000L;
        grantMemory(state);

        player.sendMessage(ChatColor.LIGHT_PURPLE + "[히스이] R 활성화! 15초 동안 강화됩니다.");
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 0.9f);

        // TODO: 실제 R1 스킬 로직 연결
        // 예: rSkill.castFirst(player);
    }

    // =========================
    // 패시브용 기본 메서드
    // =========================
    public void grantMemory(HisuiState state) {
        state.memoryStack = Math.min(2, state.memoryStack + 1);
        state.memoryExpireAt = System.currentTimeMillis() + 5000L;
    }

    public void addIaiStack(Player player, int amount) {
        HisuiState state = getState(player);
        state.iaiStack = Math.min(3, state.iaiStack + amount);
        player.sendMessage(ChatColor.YELLOW + "[히스이] 거합 중첩: " + state.iaiStack);
    }

    public void consumeMemoryOnBasicAttack(Player player) {
        HisuiState state = getState(player);

        if (System.currentTimeMillis() > state.memoryExpireAt) {
            state.memoryStack = 0;
            return;
        }

        if (state.memoryStack <= 0) return;

        state.memoryStack--;

        // Q/W 쿨감 예시
        state.qCooldownEnd = Math.max(System.currentTimeMillis(), state.qCooldownEnd - 1500L);
        state.wCooldownEnd = Math.max(System.currentTimeMillis(), state.wCooldownEnd - 2000L);

        player.sendMessage(ChatColor.AQUA + "[히스이] 검의 기억 소모! 남은 중첩: " + state.memoryStack);
    }

    // =========================
    // 외부에서 쓰기 좋은 접근 메서드
    // =========================
    public HisuiState getPlayerState(Player player) {
        return getState(player);
    }

    public boolean isHisuiSkillItem(ItemStack item) {
        return getSkillId(item) != null;
    }

    // =========================
    // 내부 상태 클래스
    // =========================
    public static class HisuiState {
        public int memoryStack = 0;
        public long memoryExpireAt = 0L;

        public int iaiStack = 0;

        public long qCooldownEnd = 0L;
        public long wCooldownEnd = 0L;
        public long eCooldownEnd = 0L;
        public long rCooldownEnd = 0L;

        public boolean rActive = false;
        public long rExpireAt = 0L;
    }
}
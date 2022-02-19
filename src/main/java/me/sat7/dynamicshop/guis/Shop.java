package me.sat7.dynamicshop.guis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import me.sat7.dynamicshop.DynaShopAPI;
import me.sat7.dynamicshop.events.OnChat;
import me.sat7.dynamicshop.utilities.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.jobshook.JobsHook;
import me.sat7.dynamicshop.transactions.Calc;
import me.sat7.dynamicshop.utilities.ShopUtil;

import static me.sat7.dynamicshop.DynaShopAPI.df;
import static me.sat7.dynamicshop.utilities.LangUtil.t;
import static me.sat7.dynamicshop.utilities.LayoutUtil.l;
import static me.sat7.dynamicshop.utilities.MathUtil.Clamp;
import static me.sat7.dynamicshop.utilities.ShopUtil.GetShopMaxPage;

public final class Shop extends InGameUI
{
    public Shop()
    {
        uiType = UI_TYPE.Shop;
    }

    private final int CLOSE = 45;
    private final int PAGE = 49;
    private final int SHOP_INFO = 53;

    private Player player;
    private String shopName;
    private int page;
    FileConfiguration shopData;

    public Inventory getGui(Player player, String shopName, int page)
    {
        this.player = player;
        this.shopName = shopName;
        this.page = page;

        shopData = ShopUtil.shopConfigFiles.get(shopName).get();

        // jobreborn 플러그인 있는지 확인.
        if (!JobsHook.jobsRebornActive && shopData.contains("Options.flag.jobpoint"))
        {
            player.sendMessage(DynamicShop.dsPrefix + t("ERR.JOBS_REBORN_NOT_FOUND"));
            return null;
        }

        int maxPage = GetShopMaxPage(shopName);
        page = Clamp(page,1,maxPage);

        String uiName = "";
        if (shopData.contains("Options.title"))
        {
            uiName = shopData.getString("Options.title");
        } else
        {
            uiName = shopName;
        }

        inventory = Bukkit.createInventory(player, 54, "§3" + uiName);

        CreateCloseButton(CLOSE);

        // 페이지 버튼
        String pageLore = t("SHOP.PAGE_LORE");
        if (player.hasPermission("dshop.admin.shopedit"))
        {
            pageLore += "\n" + t("SHOP.PAGE_EDIT_LORE");
        }
        String pageString = t("SHOP.PAGE_TITLE");
        pageString = pageString.replace("{curPage}", page + "");
        pageString = pageString.replace("{maxPage}", maxPage + "");
        CreateButton(PAGE, Material.PAPER, pageString, pageLore, page);

        // 정보,설정 버튼
        String shopLore = CreateShopInfoText(player, shopName);
        String infoBtnIconName = ShopUtil.GetShopInfoIconMat();
        CreateButton(SHOP_INFO, Material.getMaterial(infoBtnIconName), "§3" + shopName, shopLore);

        // 상품목록 등록
        int idx = 0;
        for (String s : shopData.getKeys(false))
        {
            try
            {
                // 현재 페이지에 해당하는 것들만 출력
                idx = Integer.parseInt(s);
                idx -= ((page - 1) * 45);
                if (!(idx < 45 && idx >= 0)) continue;

                // 아이탬 생성
                String itemName = shopData.getString(s + ".mat"); // 메테리얼
                ItemStack itemStack = new ItemStack(Material.getMaterial(itemName), 1); // 아이탬 생성
                itemStack.setItemMeta((ItemMeta) shopData.get(s + ".itemStack")); // 저장된 메타 적용

                // 커스텀 메타 설정
                ItemMeta meta = itemStack.getItemMeta();
                String lore = "";

                // 상품
                if (shopData.contains(s + ".value"))
                {
                    lore = l("SHOP.ITEM_INFO");

                    int stock = shopData.getInt(s + ".stock");
                    int maxStock = shopData.getInt(s + ".maxStock", -1);
                    String stockStr;
                    String maxStockStr = "";

                    if (stock <= 0)
                    {
                        stockStr = t("SHOP.INF_STOCK");
                    } else if (DynamicShop.plugin.getConfig().getBoolean("UI.DisplayStockAsStack"))
                    {
                        stockStr = t("SHOP.STACKS").replace("{num}", (stock / 64) + "");
                    } else
                    {
                        stockStr = String.valueOf(stock);
                    }

                    if (maxStock != -1)
                    {
                        if (DynamicShop.plugin.getConfig().getBoolean("UI.DisplayStockAsStack"))
                        {
                            maxStockStr = t("SHOP.STACKS").replace("{num}", (maxStock / 64) + "");
                        }
                        else
                        {
                            maxStockStr = String.valueOf(maxStock);
                        }
                    }

                    double buyPrice = Calc.getCurrentPrice(shopName, s, true);
                    double sellPrice = Calc.getCurrentPrice(shopName, s, false);

                    double buyPrice2 = shopData.getDouble(s + ".value");
                    double priceSave1 = ((buyPrice / buyPrice2) * 100) - 100;
                    double priceSave2 = 100 - ((buyPrice / buyPrice2) * 100);

                    String valueChanged_Buy;
                    String valueChanged_Sell;

                    if (buyPrice - buyPrice2 >= 0.01)
                    {
                        valueChanged_Buy = t("ARROW.UP_2") + Math.round(priceSave1 * 100d) / 100d + "%";
                        valueChanged_Sell = t("ARROW.UP") + Math.round(priceSave1 * 100d) / 100d + "%";
                    } else if (buyPrice - buyPrice2 <= -0.01)
                    {
                        valueChanged_Buy = t("ARROW.DOWN_2") + Math.round(priceSave2 * 100d) / 100d + "%";
                        valueChanged_Sell = t("ARROW.DOWN") + Math.round(priceSave2 * 100d) / 100d + "%";
                    } else
                    {
                        valueChanged_Buy = "";
                        valueChanged_Sell = "";
                    }

                    if (buyPrice == sellPrice) sellPrice = buyPrice - ((buyPrice / 100) * Calc.getTaxRate(shopName));

                    String tradeType = "default";
                    if (shopData.contains(s + ".tradeType"))
                        tradeType = shopData.getString(s + ".tradeType");

                    boolean showValueChange = shopData.contains("Options.flag.showvaluechange");

                    String buyText = "";
                    String sellText = "";
                    if (!tradeType.equalsIgnoreCase("SellOnly"))
                    {
                        buyText = t("SHOP.BUY_PRICE").replace("{num}", df.format(buyPrice));
                        buyText += showValueChange ? " " + valueChanged_Buy : "";
                    }

                    if (!tradeType.equalsIgnoreCase("BuyOnly"))
                    {
                        sellText = t("SHOP.SELL_PRICE").replace("{num}", df.format(sellPrice));
                        sellText += showValueChange ? " " + valueChanged_Sell : "";
                    }

                    String pricingTypeText = "";
                    if (shopData.getInt(s + ".stock") <= 0 || shopData.getInt(s + ".median") <= 0)
                    {
                        if (!shopData.contains("Options.flag.hidepricingtype"))
                        {
                            pricingTypeText = t("SHOP.STATIC_PRICE");
                        }
                    }

                    String stockText = "";
                    if (!shopData.contains("Options.flag.hidestock"))
                    {
                        if (maxStock != -1 && shopData.contains("Options.flag.showmaxstock"))
                            stockText = t("SHOP.STOCK_2").replace("{stock}", stockStr).replace("{max_stock}", maxStockStr);
                        else
                            stockText = t("SHOP.STOCK").replace("{num}", stockStr);
                    }

                    String tradeLoreText = "";
                    if (t("SHOP.TRADE_LORE").length() > 0)
                        tradeLoreText = t("SHOP.TRADE_LORE");

                    String itemMetaLoreText = (meta != null && meta.hasLore()) ? meta.getLore().toString() : "";

                    lore = lore.replace("{\\nBuy}", buyText.isEmpty() ? "" : "\n" + buyText);
                    lore = lore.replace("{\\nSell}", sellText.isEmpty() ? "" : "\n" + sellText);
                    lore = lore.replace("{\\nStock}", stockText.isEmpty() ? "" : "\n" + stockText);
                    lore = lore.replace("{\\nPricingType}", pricingTypeText.isEmpty() ? "" : "\n" + pricingTypeText);
                    lore = lore.replace("{\\nTradeLore}", tradeLoreText.isEmpty() ? "" : "\n" + tradeLoreText);
                    lore = lore.replace("{\\nItemMetaLore}", itemMetaLoreText.isEmpty() ? "" : "\n" + itemMetaLoreText);

                    lore = lore.replace("{Buy}", buyText);
                    lore = lore.replace("{Sell}", sellText);
                    lore = lore.replace("{Stock}", stockText);
                    lore = lore.replace("{PricingType}", pricingTypeText);
                    lore = lore.replace("{TradeLore}", tradeLoreText);
                    lore = lore.replace("{ItemMetaLore}", itemMetaLoreText);

                    String temp = lore.replace(" ","");
                    if(ChatColor.stripColor(temp).startsWith("\n"))
                        lore = lore.replaceFirst("\n","");

                    if (player.hasPermission("dshop.admin.shopedit"))
                    {
                        lore += "\n" + t("SHOP.ITEM_MOVE_LORE");
                        lore += "\n" + t("SHOP.ITEM_EDIT_LORE");
                    }
                }
                // 장식용
                else
                {
                    if (player.hasPermission("dshop.admin.shopedit"))
                    {
                        lore += t("SHOP.ITEM_COPY_LORE");
                        lore += "\n" + t("SHOP.DECO_DELETE_LORE");
                    }

                    meta.setDisplayName(" ");
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                meta.setLore(new ArrayList<>(Arrays.asList(lore.split("\n"))));
                itemStack.setItemMeta(meta);
                inventory.setItem(idx, itemStack);
            } catch (Exception e)
            {
                if (!s.equalsIgnoreCase("Options"))
                {
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + "ERR.Shop.getGUI/Failed to create itemstack. yml file is corrupt. shop name: " + shopName + ", page: " + page + ", itemIndex: " + idx);
//                    for(StackTraceElement ste: e.getStackTrace())
//                    {
//                        DynamicShop.console.sendMessage(DynamicShop.dsPrefix_server+ste);
//                    }
                }
            }
        }
        return inventory;
    }

    @Override
    public void OnClickUpperInventory(InventoryClickEvent e)
    {
        player = (Player) e.getWhoClicked();

        String itemtoMove = "";
        if (DynamicShop.userInteractItem.get(player.getUniqueId()) != null)
        {
            String[] temp = DynamicShop.userInteractItem.get(player.getUniqueId()).split("/");
            if (temp.length > 1) itemtoMove = temp[1];
        }

        // 빈칸이 아닌 뭔가 클릭함
        if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR)
        {
            // 닫기버튼
            if (e.getSlot() == CLOSE)
            {
                // 표지판으로 접근한 경우에는 그냥 창을 닫음
                if (DynamicShop.userTempData.get(player.getUniqueId()).equalsIgnoreCase("sign"))
                {
                    DynamicShop.userTempData.put(player.getUniqueId(), "");
                    player.closeInventory();
                }
                else
                {
                    if (DynamicShop.plugin.getConfig().getBoolean("UI.OpenStartPageWhenClickCloseButton"))
                    {
                        DynaShopAPI.openStartPage(player);
                    } else
                    {
                        ShopUtil.closeInventoryWithDelay(player);
                    }
                }
            }
            // 페이지 이동 버튼
            else if (e.getSlot() == PAGE)
            {
                int targetPage = page;
                if (e.isLeftClick())
                {
                    if (!e.isShiftClick())
                    {
                        targetPage -= 1;
                        if (targetPage < 1)
                            targetPage = GetShopMaxPage(shopName);
                    } else if (player.hasPermission("dshop.admin.shopedit"))
                    {
                        ShopUtil.insetShopPage(shopName, page);
                    }
                } else if (e.isRightClick())
                {
                    if (!e.isShiftClick())
                    {
                        targetPage += 1;
                        if (targetPage > GetShopMaxPage(shopName))
                            targetPage = 1;
                    } else if (player.hasPermission("dshop.admin.shopedit"))
                    {
                        if (shopData.getInt("Options.page") > 1)
                        {
                            ShopUtil.closeInventoryWithDelay(player);

                            DynamicShop.userInteractItem.put(player.getUniqueId(), shopName + "/" + page);
                            DynamicShop.userTempData.put(player.getUniqueId(), "waitforPageDelete");
                            OnChat.WaitForInput(player);

                            player.sendMessage(DynamicShop.dsPrefix + t("MESSAGE.DELETE_CONFIRM"));
                        } else
                        {
                            player.sendMessage(DynamicShop.dsPrefix + t("MESSAGE.CANT_DELETE_LAST_PAGE"));
                        }
                        return;
                    }
                }
                DynaShopAPI.openShopGui(player, shopName, targetPage);
            }
            // 상점 설정 버튼
            else if (e.getSlot() == SHOP_INFO && e.isRightClick() && player.hasPermission("dshop.admin.shopedit"))
            {
                DynamicShop.userInteractItem.put(player.getUniqueId(), shopName + "/" + 0); // 선택한 아이탬의 인덱스 저장
                DynaShopAPI.openShopSettingGui(player, shopName);
            } else if (e.getSlot() <= 45)
            {
                // 상점의 아이탬 클릭
                int idx = e.getSlot() + (45 * (page - 1));

                // 거래화면 열기
                if (e.isLeftClick())
                {
                    if (!shopData.contains(idx + ".value")) return; // 장식용 버튼임

                    SoundUtil.playerSoundEffect(player, "tradeview");
                    DynaShopAPI.openItemTradeGui(player, shopName, String.valueOf(idx));
                    DynamicShop.userInteractItem.put(player.getUniqueId(), shopName + "/" + idx); // 선택한 아이탬의 인덱스 저장
                }
                // 아이탬 이동, 수정, 또는 장식탬 삭제
                else if (player.hasPermission("dshop.admin.shopedit"))
                {
                    DynamicShop.userInteractItem.put(player.getUniqueId(), shopName + "/" + idx); // 선택한 아이탬의 인덱스 저장
                    if (e.isShiftClick())
                    {
                        if (shopData.contains(idx + ".value"))
                        {
                            double buyValue = shopData.getDouble(idx + ".value");
                            double sellValue = buyValue;
                            if (shopData.contains(idx + ".value2"))
                            {
                                sellValue = shopData.getDouble(idx + ".value2");
                            }
                            double valueMin = shopData.getDouble(idx + ".valueMin");
                            if (valueMin <= 0.01) valueMin = 0.01;
                            double valueMax = shopData.getDouble(idx + ".valueMax");
                            if (valueMax <= 0) valueMax = -1;
                            int median = shopData.getInt(idx + ".median");
                            int stock = shopData.getInt(idx + ".stock");
                            int maxStock = shopData.getInt(idx + ".maxStock", -1);

                            ItemStack iStack = new ItemStack(e.getCurrentItem().getType());
                            iStack.setItemMeta((ItemMeta) shopData.get(idx + ".itemStack"));

                            DynaShopAPI.openItemSettingGui(player, shopName, idx, 0, iStack, buyValue, sellValue, valueMin, valueMax, median, stock, maxStock);
                        } else
                        {
                            ShopUtil.removeItemFromShop(shopName, idx);
                            DynaShopAPI.openShopGui(player, shopName, idx / 45 + 1);
                        }
                    } else if (itemtoMove.equals(""))
                    {
                        player.sendMessage(DynamicShop.dsPrefix + t("SHOP.ITEM_MOVE_SELECTED"));
                    }
                }
            }
        }
        // 빈칸 클릭함
        else
        {
            if (e.getSlot() > 45)
            {
                return;
            }

            int clickedIdx = e.getSlot() + ((page - 1) * 45);

            // 아이탬 이동. 또는 장식 복사
            if (e.isRightClick() && player.hasPermission("dshop.admin.shopedit") && !itemtoMove.equals(""))
            {
                shopData.set(clickedIdx + ".mat", shopData.get(itemtoMove + ".mat"));
                shopData.set(clickedIdx + ".itemStack", shopData.get(itemtoMove + ".itemStack"));
                shopData.set(clickedIdx + ".value", shopData.get(itemtoMove + ".value"));
                shopData.set(clickedIdx + ".value2", shopData.get(itemtoMove + ".value2"));
                shopData.set(clickedIdx + ".valueMin", shopData.get(itemtoMove + ".valueMin"));
                shopData.set(clickedIdx + ".valueMax", shopData.get(itemtoMove + ".valueMax"));
                shopData.set(clickedIdx + ".median", shopData.get(itemtoMove + ".median"));
                shopData.set(clickedIdx + ".stock", shopData.get(itemtoMove + ".stock"));
                shopData.set(clickedIdx + ".tradeType", shopData.get(itemtoMove + ".tradeType"));

                if (shopData.contains(itemtoMove + ".value"))
                {
                    shopData.set(itemtoMove, null);
                }

                ShopUtil.shopConfigFiles.get(shopName).save();

                DynaShopAPI.openShopGui(player, shopName, page);
                DynamicShop.userInteractItem.put(player.getUniqueId(), "");
            }
            // 팔렛트 열기
            else if (player.hasPermission("dshop.admin.shopedit"))
            {
                DynamicShop.userInteractItem.put(player.getUniqueId(), shopName + "/" + clickedIdx); // 선택한 아이탬의 인덱스 저장
                DynaShopAPI.openItemPalette(player, shopName, clickedIdx, 1, "");
            }
        }
    }

    private String CreateShopInfoText(Player player, String shopName)
    {
        String shopLore = l("SHOP.INFO");

        StringBuilder finalLoreText = new StringBuilder();
        if (shopData.contains("Options.lore"))
        {
            String loreTxt = shopData.getString("Options.lore");
            if (loreTxt != null && loreTxt.length() > 0)
            {
                String[] loreArray = loreTxt.split(Pattern.quote("\\n"));
                for (String s : loreArray)
                {
                    finalLoreText.append("§f").append(s).append("\n");
                }
            }
        }

        // 권한
        String finalPermText = "";
        String perm = shopData.getString("Options.permission");
        if (!(perm.length() == 0))
        {
            finalPermText += t("SHOP.PERMISSION") + "\n";
            finalPermText += t("SHOP.PERMISSION").replace("{permission}", perm) + "\n";
        }

        // 세금
        String finalTaxText = "";
        finalTaxText += t("TAX.SALES_TAX") + ":" + "\n";
        finalTaxText += t("SHOP.SHOP_INFO_DASH") + Calc.getTaxRate(shopName) + "%" + "\n";

        // 상점 잔액
        String finalShopBalanceText = "";

        if(!shopData.contains("Options.flag.hideshopbalance"))
        {
            finalShopBalanceText += t("SHOP.SHOP_BAL") + "\n";
            if (ShopUtil.getShopBalance(shopName) >= 0)
            {
                String temp = df.format(ShopUtil.getShopBalance(shopName));
                if (shopData.contains("Options.flag.jobpoint")) temp += "Points";

                finalShopBalanceText += t("SHOP.SHOP_INFO_DASH") + temp + "\n";
            } else
            {
                finalShopBalanceText += t("SHOP.SHOP_INFO_DASH") + ChatColor.stripColor(t("SHOP.SHOP_BAL_INF")) + "\n";
            }
        }

        // 영업시간
        String finalShopHourText = "";
        if (shopData.contains("Options.shophours"))
        {
            String[] temp = shopData.getString("Options.shophours").split("~");
            int open = Integer.parseInt(temp[0]);
            int close = Integer.parseInt(temp[1]);

            finalShopHourText += t("TIME.SHOPHOURS") + "\n";
            finalShopHourText += t("SHOP.SHOP_INFO_DASH") + t("TIME.OPEN") + ": " + open + "\n";
            finalShopHourText += t("SHOP.SHOP_INFO_DASH") + t("TIME.CLOSE") + ": " + close + "\n";
        }

        // 상점 좌표
        String finalShopPosText = "";
        if (shopData.contains("Options.pos1") && shopData.contains("Options.pos2"))
        {
            finalShopPosText += t("SHOP.SHOP_LOCATION_B") + "\n";
            finalShopPosText += t("SHOP.SHOP_INFO_DASH") + shopData.getString("Options.world") + "\n";
            finalShopPosText += t("SHOP.SHOP_INFO_DASH") + shopData.getString("Options.pos1") + "\n";
            finalShopPosText += t("SHOP.SHOP_INFO_DASH") + shopData.getString("Options.pos2") + "\n";
        }

        shopLore = shopLore.replace("{\\nShopLore}", finalLoreText.toString().isEmpty() ? "" : "\n" + finalLoreText);
        shopLore = shopLore.replace("{\\nPermission}", finalPermText.isEmpty() ? "" : "\n" + finalPermText);
        shopLore = shopLore.replace("{\\nTax}", "\n" + finalTaxText);
        shopLore = shopLore.replace("{\\nShopBalance}", finalShopBalanceText.isEmpty() ? "" : "\n" + finalShopBalanceText);
        shopLore = shopLore.replace("{\\nShopHour}", finalShopHourText.isEmpty() ? "" : "\n" + finalShopHourText);
        shopLore = shopLore.replace("{\\nShopPosition}", finalShopPosText.isEmpty() ? "" : "\n" + finalShopPosText);

        shopLore = shopLore.replace("{ShopLore}", finalLoreText);
        shopLore = shopLore.replace("{Permission}", finalPermText);
        shopLore = shopLore.replace("{Tax}", finalTaxText);
        shopLore = shopLore.replace("{ShopBalance}", finalShopBalanceText);
        shopLore = shopLore.replace("{ShopHour}", finalShopHourText);
        shopLore = shopLore.replace("{ShopPosition}", finalShopPosText);

        String temp = shopLore.replace(" ","");
        if(ChatColor.stripColor(temp).startsWith("\n"))
            shopLore = shopLore.replaceFirst("\n","");

        // 어드민이면----------
        if (player.hasPermission("dshop.admin.shopedit"))
            shopLore += "\n";

        // 플래그
        String finalFlagText = "";
        if (player.hasPermission("dshop.admin.shopedit"))
        {
            if (shopData.contains("Options.flag") && shopData.getConfigurationSection("Options.flag").getKeys(false).size() > 0)
            {
                finalFlagText = t("SHOP.FLAGS") + "\n";
                for (String s : shopData.getConfigurationSection("Options.flag").getKeys(false))
                {
                    finalFlagText += t("SHOP.FLAGS_ITEM").replace("{flag}", s) + "\n";
                }
                finalFlagText += "\n";
            }
        }
        shopLore += finalFlagText;

        if (player.hasPermission("dshop.admin.shopedit"))
        {
            shopLore += t("SHOP_SETTING.SHOP_SETTINGS_LORE");
        }

        return shopLore;
    }
}

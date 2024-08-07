package org.example.mc.siteintegration.items;

import java.util.concurrent.CompletableFuture;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.example.mc.siteintegration.constants.UrlConstants;
import org.example.mc.siteintegration.helpers.ItemSerializer;
import org.example.mc.siteintegration.utils.PlayerError;
import org.example.mc.siteintegration.utils.PlayerMessageUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class PullItems implements CommandExecutor {

    private Player player;
    private ItemStack shulkerBoxInMainHand;
    private Integer itemTicketId;
    private Integer countEmptySlots = 0;
    private BlockStateMeta shulkerMeta;
    private PlayerMessageUtil messageUtil;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try{
            if (!(sender instanceof Player)) {
                sender.sendMessage("Ця команда може бути активована тільки гравцем");
                return false;
            }

            this.player = (Player) sender;
            this.messageUtil = new PlayerMessageUtil(player);
            this.itemTicketId = Integer.parseInt(args[0]);;
            this.shulkerBoxInMainHand = player.getInventory().getItemInMainHand();

            inspectShulkerInHand();
            inspectShulkerContents();
            fetchGetItemTicketInfo();
        } catch (PlayerError e){
            messageUtil.toActionBar(e.getMessage());
            return false;
        } catch (Exception e){
            Bukkit.getLogger().warning(e.getMessage());
            messageUtil.toChat("Помилка в плагіні, будь ласка повідомте адмінів");
            return false;
        }

        return true;
    }

    private void fetchGetItemTicketInfo() throws Exception{
        messageUtil.toActionBar("&eТриває операція");

        String url = UrlConstants.BACKEND_URL + "mc/item_ticket/countSlots?username="+ player.getName() + "&itemTicketId=" + itemTicketId;
        HttpGet request = new HttpGet(url);

        CompletableFuture.runAsync(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) { 

            HttpResponse response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();

            String responseBody = EntityUtils.toString(response.getEntity());
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(responseBody);

            if(statusCode >= 300 && statusCode < 600) {

                String errorMessage = jsonResponse.get("message").toString();

                throw new PlayerError("&c" + errorMessage);
            }

            Integer needCountSlots = Integer.parseInt(jsonResponse.get("countSlots").toString());

            if(countEmptySlots < needCountSlots) throw new PlayerError("&cВ шалкері не вистачає місця");

            fetchPullItems();
        } catch (PlayerError e){
            messageUtil.toChat(e.getMessage());
        } catch(Exception e){
            throw new Error(e);
        }
    });
    }

    private void containNewItemToShulker(JSONArray serializedArray) throws Exception{
        ItemStack[] itemStackItems = new ItemStack[serializedArray.size()];

        for (int i = 0; i < serializedArray.size(); i++) {
            itemStackItems[i] = ItemSerializer.deserializeItem((String) serializedArray.get(i));
        }

        ShulkerBox shulkerBox = (ShulkerBox) shulkerMeta.getBlockState();
        ItemStack[] contentsArray = shulkerBox.getInventory().getContents();

        for (int i = 0, y = 0; i < contentsArray.length; i++) {
            ItemStack itemSlot = contentsArray[i];

            if(itemSlot == null){
                contentsArray[i] = itemStackItems[y];
                y+= 1;

                if (y == serializedArray.size()) break;
            }
        }

        shulkerBox.getInventory().setContents(contentsArray);
        shulkerMeta.setBlockState(shulkerBox);
    }

    private void fetchDeleteItems() throws Exception  {
        String url = UrlConstants.BACKEND_URL + "mc/user/items/delete/" + itemTicketId + '/' + player.getName();
        HttpDelete request = new HttpDelete(url);

        CompletableFuture.runAsync(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) { 

            HttpResponse response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();

            String responseBody = EntityUtils.toString(response.getEntity());

            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(responseBody);

            if(statusCode >= 300 && statusCode < 600) {
                String errorMessage = jsonResponse.get("message").toString();

                throw new PlayerError("&c" + errorMessage);
            }
        } catch (PlayerError e){
            messageUtil.toChat(e.getMessage());
        } catch(Exception e){
            throw new Error(e);
        }
    });
    }

    private void fetchPullItems() throws Exception  {
        messageUtil.toActionBar("&eТриває операція");

        String url = UrlConstants.BACKEND_URL + "mc/user/items/pull/" + itemTicketId;
        HttpPut request = new HttpPut(url);

        CompletableFuture.runAsync(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) { 

            HttpResponse response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();

            String responseBody = EntityUtils.toString(response.getEntity());

            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(responseBody);

            if(statusCode >= 300 && statusCode < 600) {
                String errorMessage = jsonResponse.get("message").toString();

                throw new PlayerError("&c" + errorMessage);
            }

            if (!player.isOnline()) throw new Error("&cГравець вийшов з гри.");

            ItemStack currentItemInMainHand = player.getInventory().getItemInMainHand();
            if (!currentItemInMainHand.equals(shulkerBoxInMainHand)) {
                throw new PlayerError("&cВи більше не тримаєте шалкер у руці.");
            }

            JSONArray serializedArray = (JSONArray) jsonResponse.get("data");
            containNewItemToShulker(serializedArray);

            shulkerBoxInMainHand.setItemMeta(shulkerMeta);
            
            messageUtil.toActionBar("&aВи успішно забрали предмети");

            fetchDeleteItems();
        } catch (PlayerError e){
            messageUtil.toChat(e.getMessage());
        } catch(Exception e){
            throw new Error(e);
        }
    });
    }

    private void inspectShulkerContents() {
        ShulkerBox shulkerBox = (ShulkerBox) shulkerMeta.getBlockState();

        ItemStack[] contentsArray = shulkerBox.getInventory().getContents();

        for (int i = 0; i < contentsArray.length; i++) {
            ItemStack itemSlot = contentsArray[i];

            if (itemSlot == null) countEmptySlots+= 1;
        }
    }

    private void inspectShulkerInHand() throws Exception{
        this.shulkerMeta = (BlockStateMeta) player.getInventory().getItemInMainHand().getItemMeta();

        if (shulkerMeta == null || !(shulkerMeta.getBlockState() instanceof ShulkerBox)) {
            throw new PlayerError("&cВ руках повинен бути шалкер");
        }
    }
}
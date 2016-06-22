/*
 *
 * Copyright 2016 The Symphony Software Foundation
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.symphonyoss.client.examples.hashtagbot;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.client.SymphonyClient;
import org.symphonyoss.client.SymphonyClientFactory;
import org.symphonyoss.client.model.AttribTypes;
import org.symphonyoss.client.model.Chat;
import org.symphonyoss.client.model.NodeTypes;
import org.symphonyoss.client.model.SymAuth;
import org.symphonyoss.client.services.ChatListener;
import org.symphonyoss.client.services.ChatServiceListener;
import org.symphonyoss.client.services.PresenceListener;
import org.symphonyoss.client.util.MlMessageParser;
import org.symphonyoss.symphony.agent.model.Message;
import org.symphonyoss.symphony.agent.model.MessageSubmission;
import org.symphonyoss.symphony.clients.AuthorizationClient;
import org.symphonyoss.symphony.pod.model.Stream;
import org.symphonyoss.symphony.pod.model.User;
import org.symphonyoss.symphony.pod.model.UserPresence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 //        -Dkeystore.password=(Pass)
 //        -Dtruststore.password=(Pass)
 //        -Dsessionauth.url=https://localhost.symphony.com:844/sessionauth
 //        -Dkeyauth.url=https://localhost.symphony.com:8444/keyauth
 //        -Dsymphony.agent.pod.url=https://symagent.mdevlab.com:8446/pod
 //        -Dsymphony.agent.agent.url=https://symagent.mdevlab.com:8446/agent
 //        -Dcerts.dir=/dev/certs/
 //        -Dtruststore.file=/dev/certs/server.truststore
 //        -Dbot.user=hashtag.bot
 *
 *
 *
 * Created by Frank Tarsillo on 5/15/2016.
 */
public class HashtagBot implements ChatListener, ChatServiceListener, PresenceListener {

    private final Logger logger = LoggerFactory.getLogger(HashtagBot.class);
    private final ConcurrentHashMap<String, Hashtag> hashtags = new ConcurrentHashMap<>();
    private SymphonyClient symClient;

    public HashtagBot() {

        loadAllHashtags();
        init();


    }

    public static void main(String[] args) {


        System.out.println("HelpDeskBot starting...");
        new HashtagBot();

    }

    public void init() {


        try {

            symClient = SymphonyClientFactory.getClient(SymphonyClientFactory.TYPE.BASIC);

            logger.debug("{} {}", System.getProperty("sessionauth.url"),
                    System.getProperty("keyauth.url"));


            AuthorizationClient authClient = new AuthorizationClient(
                    System.getProperty("sessionauth.url"),
                    System.getProperty("keyauth.url"));


            authClient.setKeystores(
                    System.getProperty("truststore.file"),
                    System.getProperty("truststore.password"),
                    System.getProperty("certs.dir") + System.getProperty("bot.user") + ".p12",
                    System.getProperty("keystore.password"));

            SymAuth symAuth = authClient.authenticate();


            symClient.init(
                    symAuth,
                    System.getProperty("bot.user") + "@markit.com",
                    System.getProperty("symphony.agent.agent.url"),
                    System.getProperty("symphony.agent.pod.url")
            );

            //This is not needed, but added for future use.
            symClient.getPresenceService().registerPresenceListener(this);


            symClient.getChatService().registerListener(this);


            MessageSubmission aMessage = new MessageSubmission();
            aMessage.setFormat(MessageSubmission.FormatEnum.TEXT);
            aMessage.setMessage("Hello master, I'm alive again....");



            Chat chat = new Chat();
            chat.setLocalUser(symClient.getLocalUser());
            Set<User> remoteUsers = new HashSet<>();
            remoteUsers.add(symClient.getUsersClient().getUserFromEmail("call.home.user@domain.com"));
            chat.setRemoteUsers(remoteUsers);
            chat.registerListener(this);
            chat.setStream(symClient.getStreamsClient().getStream(remoteUsers));


            symClient.getChatService().addChat(chat);

            symClient.getMessageService().sendMessage(chat, aMessage);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void onUserPresence(UserPresence userPresence) {

        logger.debug("Received user presence update: {}:{}", userPresence.getUid(), userPresence.getCategory());


    }

    public void onChatMessage(Message message) {
        if (message == null)
            return;

        logger.debug("TS: {}\nFrom ID: {}\nMessage: {}\nMessage Type: {}",
                message.getTimestamp(),
                message.getFromUserId(),
                message.getMessage(),
                message.getMessageType());

        processMessage(message);

    }

    public void processMessage(Message message) {


        //MessageMlParser messageMlParser = new MessageMlParser(message.getMessage(), symClient);


        MlMessageParser mlMessageParser;

       try {
           mlMessageParser = new MlMessageParser(symClient);
           mlMessageParser.parseMessage(message.getMessage());
       }catch(Exception e){
           logger.error("Could not parse message {}", message.getMessage(),e);
           sendUsage(message);
           return;
       }

        String[] chunks = mlMessageParser.getTextChunks();


        if (chunks.length > 1) {

            String command = chunks[0].toLowerCase().trim();

            switch (command) {
                case "add":
                    logger.debug("Add command received from {} ", message.getFromUserId());
                    addHashtag(mlMessageParser, message);
                    break;
                case "update":
                    logger.debug("Update command received from {} ", message.getFromUserId());
                    updateHashtag(mlMessageParser, message);
                    break;
                case "remove":
                    logger.debug("Remove command received from {} ", message.getFromUserId());
                    removeHashtag(mlMessageParser, message);
                    break;
                case "search":
                    logger.debug("Search command received from {} ", message.getFromUserId());
                    searchHashtag(mlMessageParser, message);
                    break;
                default:
                    sendUsage(message);
                    break;

            }
        } else {
            sendUsage(message);

        }


    }

    private void addHashtag(MlMessageParser mlMessageParser, Message message) {

        String[] chunks = mlMessageParser.getTextChunks();

        if (chunks.length < 3) {

            logger.error("Not enough arguments to add hashtag from user {}", message.getFromUserId());
            sendUsage(message);
            return;
        }

        if (chunks[1].startsWith("#")) {

            Hashtag hashtag = new Hashtag();
            hashtag.setName(chunks[1].substring(1));

            HashtagDef hashtagDef = new HashtagDef();
            hashtagDef.setUserId(message.getFromUserId());
            hashtagDef.setDefinition( mlMessageParser.getHtmlStartingFromNode(NodeTypes.HASHTAG.toString(), AttribTypes.TAG.toString(),chunks[1].substring(1)));



            Hashtag cHashtag = hashtags.get(hashtag.getName());


            if (cHashtag != null) {

                cHashtag.getDefinitions().add(hashtagDef);
                cHashtag.setLastChange(System.currentTimeMillis());
                sendHashtagMessage(cHashtag, message);
            } else {
                ArrayList<HashtagDef> defs = new ArrayList<>();
                defs.add(hashtagDef);
                hashtag.setDefinitions(defs);
                hashtag.setLastChange(System.currentTimeMillis());
                hashtags.put(hashtag.getName(), hashtag);
                cHashtag = hashtag;
                sendHashtagMessage(hashtag, message);
            }

            writeHashtagToFile(cHashtag);

        }

    }

    private void updateHashtag(MlMessageParser mlMessageParser, Message message) {
        String[] chunks = mlMessageParser.getTextChunks();


        if (chunks.length < 4) {
            logger.error("Not enough arguments to add hashtag from user {}", message.getFromUserId());
            sendUsage(message);
            return;
        }


        if (chunks[1].startsWith("#") && chunks[2].startsWith("#")) {

            Hashtag hashtag =  new Hashtag();
            hashtag.setName(chunks[1].substring(1));

            Hashtag cHashtag = hashtags.get(hashtag.getName());


            if (cHashtag != null) {

                ArrayList<HashtagDef> defs = cHashtag.getDefinitions();

                if (defs != null) {
                    Hashtag hNum = new Hashtag();
                    hNum.setName(chunks[2].substring(1));


                    try {
                        HashtagDef hashtagDef = defs.get(Integer.parseInt(hNum.getName()) - 1);

                        if (hashtagDef != null) {

                            logger.debug("Updated message: {}", mlMessageParser.getHtmlStartingFromText(chunks[2]) );

                            hashtagDef.setDefinition(mlMessageParser.getHtmlStartingFromNode(NodeTypes.HASHTAG.toString(), AttribTypes.TAG.toString(),chunks[2].substring(1)));



                        } else {
                            sendUsage(message);
                            return;
                        }
                    } catch (IndexOutOfBoundsException e) {

                        sendDefinitionNotFound(message, hashtag.getName(), hNum.getName());
                    }

                } else {
                    sendUsage(message);
                    return;
                }


                cHashtag.setLastChange(System.currentTimeMillis());
                sendHashtagMessage(cHashtag, message);
                writeHashtagToFile(cHashtag);

            } else {

                sendNotFound(message, hashtag.getName());

            }


        }


    }

    private void removeHashtag(MlMessageParser mlMessageParser, Message message) {
        String[] chunks = mlMessageParser.getTextChunks();

        if (chunks.length < 2 && !chunks[1].startsWith("#") ) {
            sendUsage(message);
            return;
        }


        Hashtag hashtag = new Hashtag();
        hashtag.setName(chunks[1].substring(1));

        Hashtag cHashtag = hashtags.get(hashtag.getName());

        try {
            if (cHashtag != null) {

                if (chunks.length == 3 && chunks[2].startsWith("#")) {

                    int val = Integer.parseInt(chunks[2].substring(1));

                    if ((val - 1) < cHashtag.getDefinitions().size())
                        cHashtag.getDefinitions().remove(val - 1);

                    sendHashtagMessage(cHashtag, message);
                    writeHashtagToFile(cHashtag);
                } else {

                    hashtags.remove(hashtag.getName());
                    sendRemovedHashtag(message, hashtag.getName());
                    removeHashtagFile(hashtag);
                }


            } else {
                sendNotFound(message, hashtag.getName());
            }

        } catch (Exception e) {
            logger.error("", e);
            sendNotFound(message, hashtag.getName());
        }


    }

    private void searchHashtag(MlMessageParser mlMessageParser, Message message) {

        String[] chunks = mlMessageParser.getTextChunks();

        if (chunks.length < 2 && !chunks[1].startsWith("#") ) {
            sendUsage(message);
            return;
        }

        Hashtag hashtag = new Hashtag();
        hashtag.setName(chunks[1].substring(1));


        Hashtag cHashtag = hashtags.get(hashtag.getName());

        if (cHashtag != null) {
            sendHashtagMessage(cHashtag, message);
        } else {
            sendNotFound(message, hashtag.getName());
        }


    }

    private void sendNotFound(Message message, String hashtag) {

        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.MESSAGEML);
        aMessage.setMessage("<messageML><br/>Sorry..hashtag <hash tag=\"" + hashtag + "\"/> not found.<br/></messageML>");

        Stream stream = new Stream();
        stream.setId(message.getStream());
        try {
            symClient.getMessagesClient().sendMessage(stream, aMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    private void sendDefinitionNotFound(Message message, String hashtag, String num) {

        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.MESSAGEML);
        aMessage.setMessage("<messageML><br/>Sorry..hashtag <hash tag=\"" + hashtag + "\"/> <hash tag=\"" + num + "\"/> not found.<br/></messageML>");

        Stream stream = new Stream();
        stream.setId(message.getStream());
        try {
            symClient.getMessagesClient().sendMessage(stream, aMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void sendUsage(Message message) {

        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.MESSAGEML);
        aMessage.setMessage("<messageML>Sorry...  <br/><b>Check the usage:</b><br/>" +
                "<b>   Add</b>        #hashtag definition<br/>" +
                "<b>   Update</b>  #hashtag #(num) definition<br/>" +
                "<b>   Remove</b> #hashtag #(num) <br/>" +
                "<b>   Search</b>   #hashtag<br/></messageML>"
        );

        Stream stream = new Stream();
        stream.setId(message.getStream());
        try {
            symClient.getMessagesClient().sendMessage(stream, aMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    private void sendRemovedHashtag(Message message, String hashtag) {

        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.MESSAGEML);
        aMessage.setMessage("<messageML><br/>Completely removed hashtag <b>#" + hashtag + "</b></messageML>");

        Stream stream = new Stream();
        stream.setId(message.getStream());
        try {
            symClient.getMessagesClient().sendMessage(stream, aMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void sendHashtagMessage(Hashtag hashtag, Message message) {

        StringBuilder out = new StringBuilder();

        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.MESSAGEML);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss z");
        Date date = new Date(hashtag.getLastChange());


        out.append("<messageML>  <br/>Definition for <hash tag=\"").append(hashtag.getName()).append("\"/>: <br/>Last modified [").append(simpleDateFormat.format(date)).append("]<br/> ");

        int i = 1;
        for (HashtagDef def : hashtag.getDefinitions()) {

            out.append("<br/>   <hash tag=\"").append(i).append("\"/>:  ");
            out.append(def.getDefinition());
            try {
                out.append("<br/>         by <mention email=\"").append(symClient.getUsersClient().getUserFromId(def.getUserId()).getEmailAddress()).append("\"/>");
            } catch (Exception e) {
                logger.error("Could not append mention due to failed email lookup from userId: {} ", def.getUserId());
            }

            ++i;
        }

        out.append("</messageML>");
        aMessage.setMessage(out.toString());

        logger.debug("{}", out.toString());

        Stream stream = new Stream();
        stream.setId(message.getStream());
        try {
            symClient.getMessagesClient().sendMessage(stream, aMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    public void onNewChat(Chat chat) {

        chat.registerListener(this);

        logger.debug("New chat session detected on stream {} with {}", chat.getStream().getId(), chat.getRemoteUsers());
    }

    public void onRemovedChat(Chat chat) {

    }


    private void writeHashtagToFile(Hashtag hashtag) {

        try {
            Gson gson = new Gson();
            FileWriter jsonFile = new FileWriter(System.getProperty("files.json") + hashtag.getName() + ".json");
            gson.toJson(hashtag, jsonFile);
            jsonFile.flush();
            jsonFile.close();

        } catch (IOException e) {
            logger.error("Could not write file for hashtag {}", hashtag.getName(), e);
        }

    }

    private void removeHashtagFile(Hashtag hashtag) {


        new File(System.getProperty("files.json") + hashtag.getName() + ".json").delete();


    }


    private void loadAllHashtags() {

        File[] files = new File(System.getProperty("files.json")).listFiles();

        if(files==null){
            logger.error("Failed to load locate directory [{}] for json pre-load..exiting",System.getProperty("files.json") );
            System.exit(1);
        }
        Gson gson = new Gson();

        for (File file : files) {

            try {
                Hashtag hashtag = gson.fromJson(new FileReader(file), Hashtag.class);
                hashtags.put(hashtag.getName(), hashtag);

            } catch (IOException e) {
                logger.error("Could not load json {} ", file.getName(), e);
            }
        }


    }

}
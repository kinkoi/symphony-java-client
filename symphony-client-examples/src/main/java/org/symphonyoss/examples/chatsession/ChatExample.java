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

package org.symphonyoss.examples.chatsession;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.client.SymphonyClient;
import org.symphonyoss.client.SymphonyClientFactory;
import org.symphonyoss.client.model.Chat;
import org.symphonyoss.client.model.SymAuth;
import org.symphonyoss.client.services.ChatListener;
import org.symphonyoss.client.services.ChatServiceListener;
import org.symphonyoss.symphony.agent.model.Message;
import org.symphonyoss.symphony.agent.model.MessageSubmission;
import org.symphonyoss.symphony.clients.AuthorizationClient;
import org.symphonyoss.symphony.pod.model.User;
import java.util.HashSet;
import java.util.Set;


/**
 *
 *
 * Simple example of the ChatService.
 *
 * It will send a message to a call.home.user and listen/create new Chat sessions.
 *
 *
 *
 * REQUIRED VM Arguments or System Properties:
 *
 *        -Dsessionauth.url=https://pod_fqdn:port/sessionauth
 *        -Dkeyauth.url=https://pod_fqdn:port/keyauth
 *        -Dsymphony.agent.pod.url=https://agent_fqdn:port/pod
 *        -Dsymphony.agent.agent.url=https://agent_fqdn:port/agent
 *        -Dcerts.dir=/dev/certs/
 *        -Dkeystore.password=(Pass)
 *        -Dtruststore.file=/dev/certs/server.truststore
 *        -Dtruststore.password=(Pass)
 *        -Dbot.user=bot.user1
 *        -Dbot.domain=@domain.com
 *        -Duser.call.home=frank.tarsillo@markit.com
 *
 *
 *
 *
 * Created by Frank Tarsillo on 5/15/2016.
 */
public class ChatExample implements ChatListener, ChatServiceListener {


    private final Logger logger = LoggerFactory.getLogger(org.symphonyoss.examples.chatsession.ChatExample.class);
    private SymphonyClient symClient;

    public ChatExample() {


        init();


    }

    public static void main(String[] args) {


        System.out.println("ChatExample starting...");
        new org.symphonyoss.examples.chatsession.ChatExample();

    }

    public void init() {


        try {

            //Create a basic client instance.
            symClient = SymphonyClientFactory.getClient(SymphonyClientFactory.TYPE.BASIC);

            logger.debug("{} {}", System.getProperty("sessionauth.url"),
                    System.getProperty("keyauth.url"));


            //Init the Symphony authorization client, which requires both the key and session URL's.  In most cases,
            //the same fqdn but different URLs.
            AuthorizationClient authClient = new AuthorizationClient(
                    System.getProperty("sessionauth.url"),
                    System.getProperty("keyauth.url"));


            //Set the local keystores that hold the server CA and client certificates
            authClient.setKeystores(
                    System.getProperty("truststore.file"),
                    System.getProperty("truststore.password"),
                    System.getProperty("certs.dir") + System.getProperty("bot.user") + ".p12",
                    System.getProperty("keystore.password"));

            //Create a SymAuth which holds both key and session tokens.  This will call the external service.
            SymAuth symAuth = authClient.authenticate();

            //With a valid SymAuth we can now init our client.
            symClient.init(
                    symAuth,
                    System.getProperty("bot.user") + System.getProperty("bot.domain"),
                    System.getProperty("symphony.agent.agent.url"),
                    System.getProperty("symphony.agent.pod.url")
            );

             //Will notify the bot of new Chat conversations.
            symClient.getChatService().registerListener(this);

            //A message to send when the BOT comes online.
            MessageSubmission aMessage = new MessageSubmission();
            aMessage.setFormat(MessageSubmission.FormatEnum.TEXT);
            aMessage.setMessage("Hello master, I'm alive again....");


            //Creates a Chat session with that will receive the online message.
            Chat chat = new Chat();
            chat.setLocalUser(symClient.getLocalUser());
            Set<User> remoteUsers = new HashSet<>();
            remoteUsers.add(symClient.getUsersClient().getUserFromEmail(System.getProperty("user.call.home")));
            chat.setRemoteUsers(remoteUsers);
            chat.registerListener(this);
            chat.setStream(symClient.getStreamsClient().getStream(remoteUsers));

            //Add the chat to the chat service, in case the "master" continues the conversation.
            symClient.getChatService().addChat(chat);


            //Send a message to the master user.
            symClient.getMessageService().sendMessage(chat, aMessage);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    //Chat sessions callback method.
    public void onChatMessage(Message message) {
        if (message == null)
            return;

        logger.debug("TS: {}\nFrom ID: {}\nMessage: {}\nMessage Type: {}",
                message.getTimestamp(),
                message.getFromUserId(),
                message.getMessage(),
                message.getMessageType());



    }

    public void onNewChat(Chat chat) {

        chat.registerListener(this);

        logger.debug("New chat session detected on stream {} with {}", chat.getStream().getId(), chat.getRemoteUsers());
    }

    public void onRemovedChat(Chat chat) {

    }

}

package com.cooksys.assessment.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientHandler implements Runnable {
    private Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private Socket socket;

    public static Map<String, ClientHandler> activeUserMap = new HashMap<>();

    public ClientHandler(Socket socket) {
        super();
        this.socket = socket;
    }

    public void run() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

            while (!socket.isClosed()) {
                String raw = reader.readLine();
                Message message = mapper.readValue(raw, Message.class);

                // Direct message to a user
                if (message.getCommand().startsWith("@")) {
                    String username = message.getCommand().substring(1);
                    boolean receiverIsActiveUser = activeUserMap.containsKey(username);

                    if (receiverIsActiveUser) {
                        ClientHandler targetUser = activeUserMap.get(username);

                        Object[] messageArgs = {new Date(), message.getUsername(), message.getContents()};
                        String messageFormattedString = formatMessage("{0} <{1}> (whisper): {2}", messageArgs);
                        message.setContents(messageFormattedString);

                        String messageToUser = mapper.writeValueAsString(message);

                        sendDirectMessageToUser(targetUser, messageToUser);
                    } else {
                        String noSuchUser = "No such user: " + username;
                        message.setContents(noSuchUser);

                        String errorResponse = mapper.writeValueAsString(message);
                        writer.write(errorResponse);
                        writer.flush();
                    }

                }

                switch (message.getCommand()) {
                    case "connect":
                        String username = message.getUsername();
                        log.info("user <{}> connected", username);

                        if (!userAlreadyExists(username)) {
                            addActiveUser(message.getUsername(), this);

                            Object[] connectArgs = {new Date(), message.getUsername()};
                            String connectionString = formatMessage("{0} <{1}> has connected", connectArgs);

                            message.setContents(connectionString);

                            String userHasConnected = mapper.writeValueAsString(message);
                            sendMessageToAllUsers(userHasConnected);
                        } else {
                            String userNameTaken = "Username <" + username + "> " + "is taken. Please try another username.";
                            message.setContents(userNameTaken);
                            String nameTaken = mapper.writeValueAsString(message);
                            writer.write(nameTaken);
                            writer.flush();
                            this.socket.close();
                        }
                        break;

                    case "disconnect":
                        log.info("user <{}> disconnected", message.getUsername());

                        Object[] disconnectArgs = {new Date(), message.getUsername()};
                        String disconnectFormattedString = formatMessage("{0} <{1}> has disconnected", disconnectArgs);
                        message.setContents(disconnectFormattedString);

                        String userHasDisconnected = mapper.writeValueAsString(message);
                        sendMessageToAllUsers(userHasDisconnected);

                        activeUserMap.remove(message.getUsername());
                        this.socket.close();
                        break;

                    case "echo":
                        log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());

                        Object[] echoArgs = {new Date(), message.getUsername(), message.getContents()};
                        String echoFormattedString = formatMessage("{0} <{1}> (echo): {2}", echoArgs);
                        message.setContents(echoFormattedString);

                        String response = mapper.writeValueAsString(message);
                        writer.write(response);
                        writer.flush();
                        break;

                    case "broadcast":
                        log.info("user <{}> broadcast message <{}>", message.getUsername(), message.getContents());

                        Object[] broadcastArgs = {new Date(), message.getUsername(), message.getContents()};
                        String broadcastFormattedString = formatMessage("{0} <{1}> (all): {2}", broadcastArgs);
                        message.setContents(broadcastFormattedString);

                        String broadcastMessage = mapper.writeValueAsString(message);
                        sendMessageToAllUsers(broadcastMessage);
                        break;

                    case "users":
                        log.info("user <{}> requested active users", message.getUsername());
                        List<String> activeUsers = getAllActiveUsers();

                        String rawActiveUsersString = new SimpleDateFormat("M/d/yyyy h:mm a").format(new Date())
                                + ": currently connected users:";
                        for (String user : activeUsers) {
                            rawActiveUsersString += "\n" + "<" + user + ">";
                        }

                        message.setContents(rawActiveUsersString);
                        String activeUsersString = mapper.writeValueAsString(message);
                        writer.write(activeUsersString);
                        writer.flush();
                }
            }
        } catch (IOException e) {
            log.error("Something went wrong :/", e);
        }
    }

    private void sendMessageToAllUsers(String messageToSend) {
        for (ClientHandler client : Server.activeUserList) {
            try {
                Socket clientSocket = client.getSocket();
                PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                printWriter.write(messageToSend);
                printWriter.flush();
            } catch (IOException e) {
                log.error("Error sending message to all users");
                e.printStackTrace();
            }
        }
    }

    private void sendDirectMessageToUser(ClientHandler targetUser, String messageToSend) {
        Socket targetSocket = targetUser.getSocket();
        try {
            PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(targetSocket.getOutputStream()));
            printWriter.write(messageToSend);
            printWriter.flush();
        } catch (IOException e) {
            log.error("Error sending direct message to: " + targetSocket);
            e.printStackTrace();
        }
    }

    private String formatMessage(String unformattedString, Object[] args) {
        MessageFormat rawString = new MessageFormat(unformattedString);
        return rawString.format(args);
    }

    private void addActiveUser(String userName, ClientHandler clientHandler) {
        activeUserMap.put(userName, clientHandler);
    }

    private List<String> getAllActiveUsers() {
        List<String> currentActiveUsers = new ArrayList<>(activeUserMap.keySet());
        return currentActiveUsers;
    }

    private boolean userAlreadyExists(String username) {
        return activeUserMap.containsKey(username);
    }


    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
}

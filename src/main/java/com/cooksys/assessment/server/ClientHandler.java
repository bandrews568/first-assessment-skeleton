package com.cooksys.assessment.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private Socket socket;

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

				switch (message.getCommand()) {
					case "connect":
						log.info("user <{}> connected", message.getUsername());

						Object[] connectArgs = {new Date(), message.getUsername()};
						MessageFormat unformattedConnectString = new MessageFormat("{0} <{1}> has connected");
						String connectFormattedString = unformattedConnectString.format(connectArgs);
						message.setContents(connectFormattedString);

						String messageUserHasConnected = mapper.writeValueAsString(message);
						sendMessageToAllActiveUsers(messageUserHasConnected);
						break;
					case "disconnect":
						log.info("user <{}> disconnected", message.getUsername());

						Object[] disconnectArgs = {new Date(), message.getUsername()};
						MessageFormat unformattedDisconnectString = new MessageFormat("{0} <{1}> has disconnected");
						String disconnectFormattedString = unformattedDisconnectString.format(disconnectArgs);
						message.setContents(disconnectFormattedString);

						String messageUserHasDisconnected = mapper.writeValueAsString(message);
						sendMessageToAllActiveUsers(messageUserHasDisconnected);

						this.socket.close();
						break;
					case "echo":
						log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());
						String response = mapper.writeValueAsString(message);
						writer.write(response);
						writer.flush();
						break;
					case "broadcast":
						log.info("user <{}> broadcast message <{}>", message.getUsername(), message.getContents());

						Object[] broadcastArgs = {new Date(), message.getUsername(), message.getContents()};
						MessageFormat unformattedBroadcastString = new MessageFormat("{0} <{1}> (all): {2}");
						String formattedString = unformattedBroadcastString.format(broadcastArgs);
						message.setContents(formattedString);

						String messageToBroadCast = mapper.writeValueAsString(message);
						sendMessageToAllActiveUsers(messageToBroadCast);
						break;
				}
			}

		} catch (IOException e) {
			log.error("Something went wrong :/", e);
		}
	}

	public void sendMessageToAllActiveUsers(String messageToSend) {
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

	public Socket getSocket() {
		return socket;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}
}

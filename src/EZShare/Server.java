package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ServerSocketFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * The server side of EZShare System. 
 * @author:
 * 
 */
public class Server {
	private JSONArray resourceList = new JSONArray();
	private JSONArray serverList = new JSONArray();
	private JSONObject clientList = new JSONObject();
	
	
	private String advertisedhostname = "";
	private int connectionintervallimit = 1;
	private int exchangeinterval = 600;
	private int port = 1024;  //port class change to Long after transfer, don't unser
	private String ip = "";
	private String secret = "2os41f58vkd9e1q4ua6ov5emlv";
	private boolean debug = true;
	private static boolean SEND = true;         //used in print debug information
	private static boolean RECEIVE = false;

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws UnknownHostException {
		Server server = new Server();
		JSONObject argsJSON = server.serverArgsParse(args);
		server.advertisedhostname = argsJSON.containsKey("advertisedhostname")?argsJSON.get("advertisedhostname").toString():server.advertisedhostname;	
		server.connectionintervallimit = argsJSON.containsKey("connectionintervallimit")?Integer.parseInt(argsJSON.get("connectionintervallimit").toString()):server.connectionintervallimit;
		server.exchangeinterval = argsJSON.containsKey("exchangeinterval")?Integer.parseInt(argsJSON.get("exchangeinterval").toString()):server.exchangeinterval;
		server.port = argsJSON.containsKey("port")?Integer.parseInt(argsJSON.get("port").toString()):server.port;
		server.secret = argsJSON.containsKey("secret")?argsJSON.get("secret").toString():server.secret;
		server.debug = argsJSON.containsKey("debug")?true:server.debug;
		server.ip = InetAddress.getLocalHost().getHostAddress();
					
		server.runInteraction(server.exchangeinterval);
		
		JSONObject serverAddr = new JSONObject();
		serverAddr.put("hostname",server.ip);
		serverAddr.put("port",server.port);
		server.serverList.add(serverAddr);
		
		ServerSocketFactory fact = ServerSocketFactory.getDefault();
		try(ServerSocket serverSocket = fact.createServerSocket(server.port)){
			
			printMessage("Starting the EZshare Server");
			printMessage("using secret: "+server.secret);
			printMessage("using advertised host name: "+server.advertisedhostname);
			printMessage("bond to port "+server.port);
			printMessage("started");
			
			boolean isActivate = true;
			while(isActivate){
				Socket client = serverSocket.accept();
				//control connection interval
				if(server.clientList.containsKey(client.getInetAddress())){
					java.util.Calendar oldClientGetIn = (java.util.Calendar) server.clientList.get(client.getInetAddress());
					java.util.Calendar successiveGetIn=java.util.Calendar.getInstance();
					int interval = successiveGetIn.compareTo(oldClientGetIn);
					if(interval < server.connectionintervallimit){
						client.close();
					}else{
						server.clientList.replace(client.getInetAddress(), successiveGetIn);
					}
				}else{
					java.util.Calendar newClientGetIn=java.util.Calendar.getInstance();
					server.clientList.put(client.getInetAddress(), newClientGetIn);
				}
				//throw a new thread responding to the client's request
				if(!client.isClosed()){
					printMessage("[IP:PORT] "+client.getInetAddress()+":"+client.getPort()+" connected");
					Thread t =new Thread(() -> server.serverClient(client));
					t.start();
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	/**
	 * responds to the client's request
	 * @param client   the client connected to the server
	 */
	@SuppressWarnings("unchecked")
	private void serverClient(Socket client){
		
		try(Socket clientSocket = client){
			
			JSONParser parser = new JSONParser();
			DataInputStream input = new DataInputStream(clientSocket.getInputStream());
			DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
		
			while(true){
				if(input.available() > 0){					
					JSONObject command = (JSONObject) parser.parse(input.readUTF());
					printDebug(this.debug, RECEIVE, command.toJSONString());
					
					JSONArray responseArray = new JSONArray();
					if(command.isEmpty()){
						String invalidCommandResponse = invalidCommand().toJSONString();
						output.writeUTF(invalidCommandResponse);
						output.flush();
						printDebug(this.debug,SEND,invalidCommandResponse);
					}
					else{
						if (command.get("command").equals("PUBLISH")) {
							responseArray.add(this.dealingPublish(command));
						}
						else if (command.get("command").equals("REMOVE")) {
							responseArray.add(this.dealingRemove(command));
						}
						else if (command.get("command").equals("SHARE")) {
							responseArray.add(this.dealingShare(command));
						}
						else if (command.get("command").equals("QUERY")) {
							responseArray = (JSONArray) this.dealingQuery(command).clone();
						}
						else if (command.get("command").equals("FETCH")) {
							responseArray = (JSONArray) this.dealingFetch(command).clone();
						}
						else if (command.get("command").equals("EXCHANGE")) {
							responseArray.add(this.dealingExchange(command));
						}
						else {
							responseArray.add(missingCommand());
						}
						
						//find the resourece matched the "fecth" request, try to send the file to client
						if(command.get("command").equals("FETCH")&&responseArray.size()>2){
							this.sendFile(responseArray, output);
						}else{
							//send other common response
							for(int i = 0; i< responseArray.size(); i++){
								String temptResponse = ((JSONObject)responseArray.get(i)).toJSONString();
								output.writeUTF(temptResponse);
								output.flush();
								printDebug(this.debug,SEND,temptResponse);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * deal with missed command
	 * @return reponse with error message
	 */
	@SuppressWarnings({ "unchecked" })
	private synchronized static JSONObject missingCommand(){
		JSONObject response = new JSONObject();
		response.put("response", "error");
		response.put("errorMessage","missing or incorrect type for command");
		return response;
	}
	
	/**
	 * deal with invalid command
	 * @return response with error message
	 */
	@SuppressWarnings({ "unchecked" })
	private synchronized static JSONObject invalidCommand() {
		JSONObject response = new JSONObject();
		response.put("response", "error");
		response.put("errorMessage", "invalid command");
		return response;
	}	
		
	/**
	 * deal with "publish" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	private synchronized JSONObject dealingPublish(JSONObject command) {
		JSONObject response = new JSONObject();
		//get the resource information
		JSONObject resource = (JSONObject) command.get("resource");
		URI uri = URI.create(resource.get("uri").toString());
		String name = resource.get("name").toString().trim();
		String description = resource.get("description").toString().trim();
		String tags = resource.get("tags").toString();
		String channel = resource.get("channel").toString().trim();
		String owner = resource.get("owner").toString().trim();
		String uriString = resource.get("uri").toString().trim();
		int duplicationNo = -1;
		boolean duplication = false;
		
		//check whether the resource alredy exists
		for(int i = 0; i< this.resourceList.size();i++){
			JSONObject storeddata = (JSONObject) this.resourceList.get(i);
			URI storeduri = URI.create(storeddata.get("uri").toString());
			if(storeduri == uri){
				duplication = true;
				duplicationNo = i;
			}
		}
		//set response based on the resource given
		if(resource == null){
			response.put("response", "error");
			response.put("errorMessage", "missing resource");
			return response;
		}else if(owner.equals("*") || uri.isAbsolute() ||
				channel.contains("\0") || name.contains("\0") ||
				description.contains("\0") || tags.contains("\0") ||
				channel.contains("\0") || owner.contains("\0") ||
				uriString.contains("\0")
				){
			response.put("response", "error");
			response.put("errorMessage", "invalid resource");
			return response;
		}else if(uri==null) {
			response.put("response", "error");
			response.put("errorMessage", "cannot publish resource");
			return response;
		}else if(duplication){
			JSONObject duplicatedData = (JSONObject) this.resourceList.get(duplicationNo);
			if(owner.equals(duplicatedData.get("owner").toString())
				&& channel.equals(duplicatedData.get("channel").toString())){
				//overwrite the old resource
				this.resourceList.set(duplicationNo, resource);	
				response.put("response", "success");
				return response;
			}else{
				response.put("response", "error");
				response.put("errorMessage", "cannot publish resource");
				return response;
			}
		}else{
			int index = this.resourceList.size();
			this.resourceList.add(index,resource);
			response.put("response", "success");
			return response;
		}	
	}
		
	/**
	 * deal with "remove" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 */
	@SuppressWarnings({ "unchecked" })
	private synchronized JSONObject dealingRemove(JSONObject command){
		JSONObject response = new JSONObject();
		//get the resource
		JSONObject resource = (JSONObject) command.get("resource");
		String name = resource.get("name").toString().trim();
		String description = resource.get("description").toString().trim();
		String tags = resource.get("tags").toString();
//			String[] tagsList = tags.split(",");
		String channel = resource.get("channel").toString().trim();
		String owner = resource.get("owner").toString().trim();
		String uriString = resource.get("uri").toString().trim();
		URI uri = URI.create(uriString);
		
		if (uriString.equals("")) {
			response.put("response", "error");
			response.put("errorMessage", "cannot remove resource");
			return response;
		}else if (owner.equals("*") || !uri.isAbsolute() ||
				channel.contains("\0") || name.contains("\0") ||
				description.contains("\0") ||  
				channel.contains("\0") || owner.contains("\0") ||
				uriString.contains("\0") || tags.contains("\0")) {
			response.put("response", "error");
			response.put("errorMessage", "invalid resource");
			return response;
		}else{
			int index;
			for(index= 0; index<this.resourceList.size(); index++){
				JSONObject tempresource = (JSONObject) this.resourceList.get(index);
				if (owner.equals(tempresource.get("owner").toString())
					&& channel.equals(tempresource.get("channel").toString())
					&& uriString.equals(tempresource.get("uri").toString())){
					this.resourceList.remove(index);
					response.put("response", "success");
					return response;
				}
			}
			response.put("response", "error");
			response.put("errorMessage", "cannot remove resource");
			return response;	
		}
	}
		
	/**
	 * deal with "share" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 */	
	@SuppressWarnings("unchecked")
	private synchronized JSONObject dealingShare(JSONObject command){
		JSONObject response = new JSONObject();
		//get the resource
		String secret = command.get("secret").toString().trim();
		JSONObject resource = (JSONObject) command.get("resource");
		URI uri = URI.create(resource.get("uri").toString().trim());
		String name = resource.get("name").toString().trim();
		String description = resource.get("description").toString().trim();
		//String tags = resource.get("tags").toString();
		String channel = resource.get("channel").toString().trim();
		String owner = resource.get("owner").toString().trim();
		String uriString = resource.get("uri").toString().trim();
		//boolean correctSecret = false;
		boolean duplication = false;
		int duplicationNo = -1;
	//			for(int i = 0; i<resourceList.size(); i++){
	//				JSONObject temptresource = (JSONObject) resourceList.get(i);
	//				if (secret.equals(temptresource.get("secret"))){
	//					correctSecret = true;
	//					index = 
	//				}
	//			}
		
		for(int i = 0; i<this.resourceList.size();i++){
			JSONObject storeddata = (JSONObject) this.resourceList.get(i);
			URI storeduri = URI.create(storeddata.get("uri").toString());
			if(storeduri.equals(uri)){
				duplication = true;
				duplicationNo = i;
			}
		}
		
		if (secret.equals("")){
			response.put("response", "error");
			response.put("errorMessage", "missing resource and/or secret");
			return response;
			
			//the followings deals with secret, having problems???
		}else if(secret.contains("\0") || !secret.equals(this.secret)){
			response.put("response", "error");
			response.put("errorMessage", "incorrect secret");
			return response;
			//the followings are fine
		}else if(owner.equals("*") ||
				channel.contains("\0") || name.contains("\0") ||
				description.contains("\0") || secret.contains("\0") ||
				channel.contains("\0") || owner.contains("\0") ||
				uriString.contains("\0")
				){
			response.put("response", "error");
			response.put("errorMessage", "invalid resource");
			return response;
		}else if(!uri.isAbsolute()||!isValidURI(uri.toString())){
			response.put("response", "error");
			response.put("errorMessage", "cannot share resource");
			return response;
		}else if(duplication){
			JSONObject duplicatedData = (JSONObject) this.resourceList.get(duplicationNo);
			if(owner.equals(duplicatedData.get("owner").toString())
				&& channel.equals(duplicatedData.get("channel").toString())){
				//replace
				this.resourceList.set(duplicationNo, resource);
				response.put("response", "success");
				return response;
			}else{
				response.put("response", "error");
				response.put("errorMessage", "cannot share resource");
				return response;
			}
		}else{
			this.resourceList.add(resourceList.size(),resource);
			response.put("response", "success");
			return response;
		}
	}
	/**
	 * check whether the uri is point to a file on the local file system that the server can read as a file
	 * @param uri uri linked to a file
	 * @return ture if the uri is valid, false if not 
	 */
	private static boolean isValidURI(String uri){
		File f = new File(uri.replaceFirst("file://", ""));
		if(f.exists()){
			return true;
		}
		else{
			return false;
		}
	}
		
	/**
	 * deal with "query" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 */		
	@SuppressWarnings("unchecked")
 	private synchronized JSONArray dealingQuery(JSONObject command) {
		JSONArray responseArray = new JSONArray();
		JSONObject response = new JSONObject();
		JSONObject resourceTemplate = (JSONObject) command.get("resourceTemplate");
		if(resourceTemplate == null){
			response.put("response", "error");
			response.put("errorMessage", "missing resourceTemplate");
			responseArray.add(response);
			return responseArray;
		}
		//get information of the queried resource 
		String name = resourceTemplate.get("name").toString().trim();
		String description = resourceTemplate.get("description").toString().trim();
		JSONArray tagsArray = (JSONArray)resourceTemplate.get("tags");
		boolean tagIsValid = true;
		ArrayList<String> tagsList = new ArrayList<String>();
		if(!tagsArray.isEmpty()){
			for(int i = 0; i<tagsArray.size();i++){
				tagsList.add(i,tagsArray.get(i).toString());
				if(( tagsList.get(i)).contains("\0")){
					tagIsValid = false;
				}
			}
		}
		String channel = resourceTemplate.get("channel").toString().trim();
		String owner = resourceTemplate.get("owner").toString().trim();
		String uriString = resourceTemplate.get("uri").toString().trim();
		//URI uri = URI.create(resourceTemplate.get("uri").toString().trim());
		
		//deal with invalid resourceTemplate, mainly breaking rules
		if(owner.equals("*") ||
				channel.contains("\0") || name.contains("\0") ||
				description.contains("\0") || !tagIsValid ||
				channel.contains("\0") || owner.contains("\0") ||
				uriString.contains("\0")
				){
			response.put("response", "error");
			response.put("errorMessage", "invalid resourceTemplate");
			responseArray.add(response);
			return responseArray;
			
		//deal with a successful query 		
		}else{
			response.put("response", "success");
			responseArray.add(response);
			int resultSize = 0;
			//get hitted resource
			for (int i = 0; i < this.resourceList.size(); i++) {
				JSONObject realResource = (JSONObject) this.resourceList.get(i);
				JSONObject temptResource = (JSONObject) realResource.clone();
				String temptName = temptResource.get("name").toString();
				String temptDescription = temptResource.get("description").toString();
				JSONArray temptTagsArray = (JSONArray)temptResource.get("tags");
				ArrayList<String> temptTagsList = new ArrayList<String>();
				if(!temptTagsArray.isEmpty()){
					for(int j = 0; j<temptTagsArray.size();j++){
						temptTagsList.add(j,temptTagsArray.get(j).toString());
					}
				}
				String temptChannel = temptResource.get("channel").toString();
				String temptOwner = temptResource.get("owner").toString();
				String temptUriString = temptResource.get("uri").toString();
				int temptTagsNo = 0;
				//get the number of matched tags
				for(int j = 0; j<tagsList.size(); j++){
					for(int k = 0; k<temptTagsList.size(); k++){
						if(temptTagsList.get(k).equals(tagsList.get(j))){
							temptTagsNo = temptTagsNo + 1;
						}
					}
				}
				//check whether the resource match
				if (channel.equals(temptChannel)) {
					if(((owner.equals("")) || owner.equals(temptOwner))){
						if ( tagsList.isEmpty() || temptTagsNo == tagsList.size()){
							if((uriString.equals("")) || uriString.equals(temptUriString)){
								if ( 
										( (!name.equals("")) && temptName.contains(name) )
										|| ( (!description.equals(""))  && temptDescription.contains(description) )
										|| ( name.equals("") && description.equals("") )
									){						

									//put the resource to the outputResource
									temptResource.replace("owner", temptOwner.equals("")?"":"*");
									if(this.advertisedhostname != ""){
										temptResource.replace("ezserver", this.advertisedhostname+":"+this.port);
									}else{
										temptResource.replace("ezserver", this.ip+":"+this.port);
									}
									responseArray.add(temptResource);
									resultSize = resultSize + 1;
								}
							}
						}
					}
					
				}
			}
			//tranfer the query request to other server
			boolean relay = (boolean) command.get("relay");
			if(relay){
				JSONArray remoResourceList = new JSONArray();
				//change "relay" to false so that other server won't tranfer the query request again
				command.replace("relay", false);
				for(int i = 0;i<this.serverList.size(); i++){
					JSONArray remoResponse = new JSONArray();
					JSONObject targetAddr = (JSONObject) this.serverList.get(i);
					String targetIP = targetAddr.get("hostname").toString();
					int targetPort = Integer.parseInt(targetAddr.get("port").toString());
					if(!(targetIP.equals(this.ip) && targetPort == this.port)){
						remoResponse = this.transferQueryCommand(targetIP, targetPort, command);
						if(remoResponse.size() > 2){
							JSONObject resultSizeJSON = (JSONObject) remoResponse.get(remoResponse.size()-1);
							resultSize += Integer.parseInt(resultSizeJSON.get("resultSize").toString());
							remoResponse.remove(remoResponse.size()-1);
							remoResponse.remove(0);
							remoResourceList.addAll(remoResponse);
						}
					}
				}
				responseArray.addAll(remoResourceList);
			}
			
			JSONObject resourceSize = new JSONObject();
			resourceSize.put("resultSize", resultSize);
			responseArray.add(resourceSize);
			return responseArray; //The ezserver should be set in the server cache
		}	
	}
	
	/**
	 * transfer query requst to other servers, only be used if the local query "success"
	 * @param ip the ip address of target server
	 * @param port the port uesd in target server
	 * @param command query command with the information of target resource
	 * @return response from other servers, just like the local query response.
	 */
	@SuppressWarnings("unchecked")
	private JSONArray transferQueryCommand(String ip, int port, JSONObject command){
		JSONArray responseArray = new JSONArray();
		JSONParser parser = new JSONParser();
		try(Socket socket = new Socket(ip,port)){
			DataInputStream input = new DataInputStream(socket.getInputStream());
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			output.writeUTF(command.toJSONString());
			output.flush();
			printDebug(this.debug,SEND,command.toJSONString());
			boolean inputEnd = false;
			while(!inputEnd){
				if(input.available() > 0){
					JSONObject response = (JSONObject) parser.parse(input.readUTF());
					responseArray.add(response);
					printDebug(this.debug,RECEIVE,response.toJSONString());
					if(response.containsKey("resultSize")){
						//this is the last response from the other server
						inputEnd = true;
					}
				}
			}
			socket.close();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch(ConnectException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return responseArray;
	}
	
	/**
	 * deal with "fetch" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 */	
	@SuppressWarnings("unchecked")
	private synchronized JSONArray dealingFetch(JSONObject command) {
		JSONObject response = new JSONObject();
		JSONArray responseArray = new JSONArray();
		JSONObject resourceTemplate = (JSONObject) command.get("resourceTemplate");
		if(resourceTemplate == null){
			response.put("response", "error");
			response.put("errorMessage", "missing resourceTemplate");
			responseArray.add(response);
			return responseArray;
		}
		URI uri = URI.create(resourceTemplate.get("uri").toString());
		//String name = resourceTemplate.get("name").toString();
		//String description = resourceTemplate.get("description").toString();
		
		String channel = resourceTemplate.get("channel").toString();
		String owner = resourceTemplate.get("owner").toString();
		//String uriString = resourceTemplate.get("uri").toString();
		boolean match = false;
		int matchNo = -1;
		JSONArray temptResourceList = (JSONArray) this.resourceList.clone();
		for (int i = 0; i<temptResourceList.size(); i++){
			JSONObject temptresource = (JSONObject)temptResourceList.get(i);
			URI temptUri = URI.create(temptresource.get("uri").toString());
			String temptChannel = temptresource.get("channel").toString();
			if(temptUri.equals(uri) && channel.equals(temptChannel)){
				match= true;
				matchNo = i;
			}
		}
		
		if(match){
			JSONObject matchresource = (JSONObject)temptResourceList.get(matchNo);
			response.put("response", "success");
			responseArray.add(response);
			
			matchresource.replace("owner", !owner.equals("")?"*":"");
			if(this.advertisedhostname != ""){
				matchresource.replace("ezserver", this.advertisedhostname+":"+this.port);
			}else{
				matchresource.replace("ezserver", this.ip+":"+this.port);
			}
			
			responseArray.add(matchresource);
			
			JSONObject resultSize = new JSONObject();
			resultSize.put("resultSize", 1);
			responseArray.add(resultSize);
			return responseArray;
			
		}else{
			response.put("response", "error");
			response.put("errorMessage", "invalid resourceTemplate");
			responseArray.add(response);
			return responseArray;
		}
	}
	/**
	 * send the fetched file to the client
	 * @param responseArray response getted from dealingFetch method, containing the file's information
	 * @param output data output stream to the client
	 */
	@SuppressWarnings("unchecked")
	private synchronized void sendFile(JSONArray responseArray, DataOutputStream output){
		JSONObject temptResource = (JSONObject)responseArray.get(1);
		//TODO   FILE ("server_files/"+temptResource.get("name")))?
		String uriString = temptResource.get("uri").toString();
		File f = new File(uriString.replaceFirst("file://", ""));
		try {
			if(f.exists()){
				// Send "success" response to client
				String fetchSuccess = ((JSONObject)responseArray.get(0)).toJSONString();
				output.writeUTF(fetchSuccess);
				output.flush();
				printDebug(this.debug,SEND,fetchSuccess);
				// Send resource back to client so that they know what the file is.
				temptResource.put("resourceSize", f.length());
				output.writeUTF(temptResource.toJSONString());
				output.flush();
				printDebug(this.debug,SEND,temptResource.toJSONString());
				// Start sending file
				RandomAccessFile byteFile = new RandomAccessFile(f,"r");
				byte[] sendingBuffer = new byte[1024*1024];
				int num;
				// While there are still bytes to send..
				while((num = byteFile.read(sendingBuffer)) > 0){
					printMessage("Uploading file: "+temptResource.get("uri"));
					printMessage("file size: "+num+" byte(s)");
					output.write(Arrays.copyOf(sendingBuffer, num));
				}
				byteFile.close();
				printMessage("Upload success!");
				String resultSize = ((JSONObject)responseArray.get(2)).toJSONString();
				
				output.writeUTF(resultSize);
				output.flush();
				printDebug(this.debug,SEND,resultSize);
			
			}
			else{
				//the file not exist, send error response to client
				JSONObject response = new JSONObject();
				response.put("response", "error");
				response.put("errorMessage", "invalid resourceTemplate");
				
				output.writeUTF(response.toJSONString());
				output.flush();
				printDebug(this.debug,SEND,response.toJSONString());
			}
		}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		}
		
	}
	
	/**
	 * exchange the serverlist with a ramdom server
	 */
	@SuppressWarnings("unchecked")
	private synchronized void serverInteraction(){
//		System.out.println("test successful exchange");
		JSONObject interactionCommand = new JSONObject();
		interactionCommand.put("command", "EXCHANGE");
		interactionCommand.put("serverList", this.serverList);
		//the automatic interaction
		if(this.serverList.size() !=0 ){
					
			java.util.Random random=new java.util.Random();
			int choosenNo =random.nextInt(this.serverList.size());				
			JSONObject selectedServer = (JSONObject) this.serverList.get(choosenNo);
			String temptip = selectedServer.get("hostname").toString();
			int temptport =  Integer.parseInt(selectedServer.get("port").toString());
			if(!(temptip.equals(this.ip)&&temptport==this.port)){
				try(Socket socket = new Socket(temptip,temptport)){
					//DataInputStream input = new DataInputStream(socket.getInputStream());
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeUTF(interactionCommand.toJSONString());
					output.flush();
					socket.close();
					printDebug(this.debug,SEND,interactionCommand.toJSONString());
				}catch (UnknownHostException e) {	
					//if the host is not connected, then remove the server from the list
					e.printStackTrace();
					this.serverList.remove(choosenNo);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}		
		}
	}
	/**
	 * schedule the interval of automatic server exchange 
	 * @param exchangeinterval exchange interval time
	 */
	private synchronized void runInteraction(int exchangeinterval){
		Timer timer = new Timer();
		timer.schedule(new  TimerTask(){
			@Override
			public void run() {
				serverInteraction();	
			}
		}, 0, 1000*exchangeinterval);
		
	}
	
	/**
	 * deal with "fetch" request, throwing different kinds of response
	 * @param command request from client
	 * @return response to the request
	 * 
	 * TODO synchronized serverList for all the servers.(not required in project specification)
	 */	
	@SuppressWarnings("unchecked")
	private synchronized JSONObject dealingExchange(JSONObject command) {
		JSONObject response = new JSONObject();
		JSONArray tempServerList = (JSONArray) command.get("serverList");
		
		if (tempServerList == null) {
			response.put("response", "error");
			response.put("errorMessage", "missing or invalid server list");
			return response;
		}
		if (tempServerList.size() == 0){
			response.put("response", "error");
			response.put("errorMessage", "missing resourceTemplate");
			return response;
		}
		//TODO  try connect, if exception, return error response
		
		//inform other servers
//		if(!command.containsKey("fromServer")){
//			for(int i = 0; i < this.serverList.size(); i++){
//				JSONObject temptServer = (JSONObject) this.serverList.get(i);
//				String temptip = temptServer.get("hostname").toString();
//				int temptport =  Integer.parseInt(temptServer.get("port").toString());
//				if(!(temptip.equals(this.ip)&&temptport==this.port)){
//					try(Socket socket = new Socket(temptip,temptport)){
//						DataInputStream input = new DataInputStream(socket.getInputStream());
//						DataOutputStream output = new DataOutputStream(socket.getOutputStream());
//						command.put("fromServer", true);
//						output.writeUTF(command.toJSONString());
//						output.flush();
//						socket.close();
//					}catch (UnknownHostException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
//			}
//		}
		
		//add the given server addresses to serverlist
		for(int i = 0; i < tempServerList.size(); i++){
			boolean duplicationServer = false;
			JSONObject tempServer = (JSONObject) tempServerList.get(i);
			String hostName = tempServer.get("hostname").toString();
			int port =  Integer.parseInt(tempServer.get("port").toString());
			
			for(int j = 0; j < this.serverList.size(); j++){
				JSONObject serverInList = (JSONObject) this.serverList.get(j);
				String serverInListhostName = serverInList.get("hostname").toString();
				int serverInListport = Integer.parseInt(serverInList.get("port").toString());
				if(serverInListhostName.equals(hostName) && port == serverInListport){
					duplicationServer = true;
				}
			}
			if(!duplicationServer){
				this.serverList.add(tempServer);
			}
		}
		
		//TODO exchange serverlist with the new servers.
		
		response.put("response", "success");
		return response;
	}
	
	/**
	 * parse the arguments for the the server
	 * @param args the command line given when the server is started 
	 * @return argsJSON containing the arguments information 
	 */
	@SuppressWarnings("unchecked")
	public JSONObject serverArgsParse(String[] args){
		
		Options option = new Options();
		option.addOption("a","advertisedhostname",true,"channel" );
		option.addOption("c","connectionintervallimit",true,"print debug information" );
		option.addOption("e","exchangeinterval",true,"resource description" );
		option.addOption("p","port",true,"exchange server list with server");
		option.addOption("s","secret",true,"fetch resources from server");
		option.addOption("d","debug",false,"server host, a domain name or a IP address");
		
		JSONObject argsJSON = new JSONObject();
		try{
			CommandLineParser parser = new DefaultParser();
			CommandLine commandLine = parser.parse(option, args);
			
			if(commandLine.hasOption("advertisedhostname")){
				argsJSON.put("advertisedhostname",commandLine.getOptionValue("advertisedhostname"));
			}
			if(commandLine.hasOption("connectionintervallimit")){
				argsJSON.put("connectionintervallimit",commandLine.getOptionValue("connectionintervallimit"));
			}
			if(commandLine.hasOption("exchangeinterval")){
				argsJSON.put("exchangeinterval",commandLine.getOptionValue("exchangeinterval"));
			}
			if(commandLine.hasOption("port")){
				argsJSON.put("port",commandLine.getOptionValue("port"));
			}
			if(commandLine.hasOption("secret")){
				argsJSON.put("secret",commandLine.getOptionValue("secret"));
			}
			if(commandLine.hasOption("debug")){
				argsJSON.put("debug",commandLine.getOptionValue("debug"));
			}
		
		}catch(UnrecognizedOptionException e){
			// TODO Auto-generated catch block
			printMessage("missing or incorrect type for command");
		}catch(org.apache.commons.cli.MissingArgumentException e){
			// TODO Auto-generated catch block
			printMessage("missing or incorrect type for command");
		} catch (org.apache.commons.cli.ParseException e) {
			// TODO Auto-generated catch block
			printMessage("missing or incorrect type for command");
		}
		return argsJSON;
	}
	
	/**
	 * print degug information, that is, the message sending to or received from other server/client
	 * @param debug whether to print
	 * @param send send to or received from others.(SEND is true, RECEIVE is false )
	 * @param message message to send to other server/client or be received from other server/client
	 */
	public static void printDebug(boolean debug, boolean send, String message){
		if(debug){
			if(send){
				printMessage("[SEND] - "+message);
			}
			else{
				printMessage("[RECEIVE] - "+message);
			}
		}
	}
	
	/**
	 * print message to user
	 * @param message
	 */
	public static void printMessage(String message){
		java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
		java.util.Calendar time = java.util.Calendar.getInstance();
		System.out.println("["+dateFormat.format(time.getTime())+"] - "+message);
	}
}

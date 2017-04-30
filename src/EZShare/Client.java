package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * the client side of EZShare system
 * @author 
 *
 */
public class Client{
//	private String ip = "sunrise.cis.unimelb.edu.au";   //"localhost"; //127.0.0.1
//	private int port =  3781;
	private String ip = "localhost";   //"localhost"; //127.0.0.1
	private int port =  1024;
	private boolean debug = false;
	private static boolean SEND = true;         //used in print debug information
	private static boolean RECEIVE = false;

	
	public static void main(String[] args){
		Client client = new Client();
		JSONParser parser = new JSONParser();
		JSONObject command = new JSONObject();
		//command is empty if it contains invalid comand
		command = client.clientArgsParse(args);
		//change the target server's host or port, and decide whether to print debug information
		if(command.containsKey("host")){
			client.ip = (String)command.get("host");
			command.remove("host");
		}
		if(command.containsKey("port")){
			client.port = Integer.parseInt(command.get("port").toString());
			command.remove("port");
		}
		if(command.containsKey("debug")){
			if((boolean)command.get("debug")){
				client.debug = true;
			}
			command.remove("debug");
		}
		//send request to server and handle the response
		try(Socket socket = new Socket(client.ip,client.port)){	
			DataInputStream input = new DataInputStream(socket.getInputStream());
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			output.writeUTF(command.toJSONString());
			output.flush();
			printDebug(client.debug,SEND,command.toJSONString());
			boolean isActive = true;
			while(isActive){
				if(input.available() > 0){
					
					JSONObject response = (JSONObject) parser.parse(input.readUTF());
					printDebug(client.debug,RECEIVE,response.toJSONString());
					
					if(command.isEmpty()){
						printMessage(response.get("errorMessage").toString());
					}
					else{
						isActive = client.handleResponse(command.get("command").toString(),response, input);			
					}
				}
			}		
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			//exit the JVM
			System.exit(0);
		}
	}
	
	/**
	 * handle the response from server
	 * @param commandType the type of the request sended to server
	 * @param response the response getted from server
	 * @param input data input stream to the server
	 * @return true if there is further reponse,
	 */
	public boolean handleResponse(String commandType, JSONObject response, DataInputStream input){
		boolean furtherResponse = false;
		switch(commandType){
			case "PUBLISH":
				if(response.get("response").equals("success")){
					printMessage("publish succeeded");
				}else{
					printMessage(response.get("errorMessage").toString());
				}
				break;
			case "REMOVE":
				if(response.get("response").equals("success")){
					printMessage("remove succeeded");
				}else{
					printMessage(response.get("errorMessage").toString());
				}
				break;
			case "SHARE":
				if(response.get("response").equals("success")){
					printMessage("share succeeded");
				}else{
					printMessage(response.get("errorMessage").toString());
				}
				break;
			case "EXCHANGE":
				if(response.get("response").equals("success")){
					printMessage("exchange succeeded");
				}else{
					printMessage(response.get("errorMessage").toString());
				}
				break;
			case "QUERY":
				if(response.containsKey("response")){
					if(response.get("response").equals("success")){
						printMessage("query succeeded");
						
					}else{
						printMessage(response.get("errorMessage").toString());
					}
					furtherResponse = true;
				}else if(response.containsKey("resultSize")){
					System.out.println("hit "+response.get("resultSize")+" resource(s)");
				}else{
					System.out.println("| "+response.get("name")+" "+response.get("tags"));
					System.out.println("| "+response.get("uri"));
					System.out.println("| =="+response.get("channel")+" ==");
					System.out.println("| ezserver: " + response.get("ezserver"));
					furtherResponse = true;
				}break;
			case "FETCH":
				if(response.containsKey("response")){
					if(response.get("response").equals("success")){
						try{
							printMessage("fetch succeeded");
							//get resource and download it
							JSONParser parser = new JSONParser();
							JSONObject resource = (JSONObject) parser.parse(input.readUTF());
							printDebug(this.debug,RECEIVE,resource.toJSONString());
							
							System.out.println("| "+resource.get("name")+" "+resource.get("tags"));
							System.out.println("| "+resource.get("uri"));
							System.out.println("| =="+resource.get("channel")+" ==");
							System.out.println("| ezserver: " + resource.get("ezserver"));
							System.out.println("| " + resource.get("resourceSize") + " bytes");
							
							// The file location
							String uriString = resource.get("uri").toString();
							int slashIndex = uriString.lastIndexOf("/");
							String fileName = uriString.substring(slashIndex+1 , uriString.length());
							
							// Create a RandomAccessFile to read and write the output file.
							RandomAccessFile downloadingFile = new RandomAccessFile(fileName, "rw");
							
							// Find out how much size is remaining to get from the server.
							long fileSizeRemaining = (Long) resource.get("resourceSize");
							
							int chunkSize = setChunkSize(fileSizeRemaining);
							
							// Represents the receiving buffer
							byte[] receiveBuffer = new byte[chunkSize];
							
							// Variable used to read if there are remaining size left to read.
							int num;
							
							//System.out.println("Downloading file:"+fileName);
							//System.out.println("file size: "+fileSizeRemaining + " byte(s)");
							
							while((num=input.read(receiveBuffer))>0){
								// Write the received bytes into the RandomAccessFile
								downloadingFile.write(Arrays.copyOf(receiveBuffer, num));
								
								// Reduce the file size left to read..
								fileSizeRemaining-=num;
								
								// Set the chunkSize again
								chunkSize = setChunkSize(fileSizeRemaining);
								receiveBuffer = new byte[chunkSize];
								
								// If you're done then break
								if(fileSizeRemaining==0){
									break;
								}
							}
						
							//System.out.println("Download success!");
							downloadingFile.close();
							//get resultSize and print it
							JSONObject resultSize = (JSONObject) parser.parse(input.readUTF());
							printDebug(this.debug,RECEIVE,resultSize.toJSONString());
							System.out.println("hit "+resultSize.get("resultSize")+" resource(s)");
							
						} catch (org.json.simple.parser.ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
							
					}else{
						printMessage(response.get("errorMessage").toString());
					}
				}break;
		}
		return furtherResponse;
	}
	/**
	 * set the chunk size of the file
	 * @param fileSizeRemaining
	 * @return the chunk size
	 */
	public static int setChunkSize(long fileSizeRemaining){
		// Determine the chunkSize
		int chunkSize=1024*1024;
		
		// If the file size remaining is less than the chunk size
		// then set the chunk size to be equal to the file size.
		if(fileSizeRemaining<chunkSize){
			chunkSize=(int) fileSizeRemaining;
		}
		
		return chunkSize;
	}
	
	/**
	 * parse the arguments for the the client
	 * @param args the command line given when the client is started 
	 * @return argsJSON containing the arguments information 
	 */
	@SuppressWarnings({ "unchecked", "finally" })
	public JSONObject clientArgsParse(String[] args){
		
		Options option = new Options();
		option.addOption("channel","channel",true,"channel" );
		option.addOption("debug","debug",false,"print debug information" );
		option.addOption("description","description",true,"resource description" );
		option.addOption("exchange","exchange",false,"exchange server list with server");
		option.addOption("fetch","fetch",false,"fetch resources from server");
		option.addOption("host","host",true,"server host, a domain name or a IP address");
		option.addOption("name","name",true,"resource name");
		option.addOption("owner","owner",true,"owner");
		option.addOption("port","port",true,"server port, an integer");
		option.addOption("publish","publish",false,"publish resource on server");
		option.addOption("query","query",false,"query for resources from server");
		option.addOption("remove","remoce",false,"remove resources from server");
		option.addOption("secret","secret",true,"secret");
		option.addOption("servers","servers",true,"server list,host1:port1,host2:port2,...");
		option.addOption("share","share",false,"share resource on server");
		option.addOption("tags","tags",true,"resource tags,tag1,tag2,tag3,...");
		option.addOption("uri","uri",true,"resource URI");
			
		JSONObject command = new JSONObject();
		try{
			CommandLineParser parser = new DefaultParser();
			CommandLine commandLine = parser.parse(option, args);
			
			if(commandLine.hasOption("publish")){
				command.put("command", "PUBLISH");
				command.put("resource",this.setResource(commandLine).toJSONObject());
			}
			
			if(commandLine.hasOption("remove")){
				command.put("command", "REMOVE");
				command.put("resource",this.setResource(commandLine).toJSONObject());
			}
			
			if(commandLine.hasOption("share")){
				command.put("command", "SHARE");
				command.put("secret", commandLine.hasOption("secret")?commandLine.getOptionValue("secret"):"");
				command.put("resource",this.setResource(commandLine).toJSONObject());
			}
			
			if(commandLine.hasOption("query")){
				command.put("command", "QUERY");
				command.put("relay", commandLine.hasOption("relay")?
						Boolean.parseBoolean(commandLine.getOptionValue("relay")):true);
				command.put("resourceTemplate",this.setResource(commandLine).toJSONObject());
			}
			
			if(commandLine.hasOption("fetch")){
				command.put("command", "FETCH");
				command.put("resourceTemplate",this.setResource(commandLine).toJSONObject());
			}
			
			if(commandLine.hasOption("exchange")){
				command.put("command", "EXCHANGE");
				JSONArray serverJSONArray = new JSONArray();
				if(commandLine.hasOption("servers")){
					String servers = commandLine.getOptionValue("servers");
					String[] serverList = servers.split(",");
					for(int i = 0; i<serverList.length ; i++){
						JSONObject singleServer = new JSONObject();
						singleServer.put("hostname", serverList[i].split(":")[0]);
						singleServer.put("port", Integer.parseInt(serverList[i].split(":")[1]));
						serverJSONArray.add(singleServer);
					}
					command.put("serverList",serverJSONArray);
				}else{
					command.put("serverList",serverJSONArray);
				}
			}
			
			command.put("debug", commandLine.hasOption("debug"));
			if(commandLine.hasOption("host")){
				command.put("host", commandLine.getOptionValue("host"));
			}
			if(commandLine.hasOption("port")){
				command.put("port", Integer.parseInt(commandLine.getOptionValue("port")));
			}
			
		} catch(UnrecognizedOptionException e){
			//invalid command
			command.clear();
		} catch(org.apache.commons.cli.MissingArgumentException e){
			//invalid command
			command.clear();
		} catch(ParseException e){
			//invalid command
			command.clear();
		}
		finally{
			return command;
		}
	}
	/**
	 * set resouce information given in command line
	 * @param commandLine the parsed command line
	 * @return the resource in JSON format
	 */
	public Resource setResource(CommandLine commandLine) {
		Resource resource = new Resource();
		
		resource.setName(commandLine.hasOption("name")?commandLine.getOptionValue("name"):"");
		resource.setTags(commandLine.hasOption("tags")?commandLine.getOptionValue("tags"):"");
		resource.setDescription(commandLine.hasOption("description")?commandLine.getOptionValue("description"):"");
		resource.setUri(commandLine.hasOption("uri")?commandLine.getOptionValue("uri"):"");
		resource.setChannel(commandLine.hasOption("channel")?commandLine.getOptionValue("channel"):"");
		resource.setOwner(commandLine.hasOption("owner")?commandLine.getOptionValue("owner"):"");
		resource.setEzserver(commandLine.hasOption("ezserver")?commandLine.getOptionValue("ezserver"):null);

		return resource;
	}
	
	/**
	 * print degug information, that is, the message sending to or received from server
	 * @param debug whether to print
	 * @param send send to or received from servers.(SEND is true, RECEIVE is false )
	 * @param message message to send to other server/client or be received from server
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
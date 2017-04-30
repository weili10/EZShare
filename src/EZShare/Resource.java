package EZShare;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * the resource used in the EZShare system
 * @author
 *
 */
public class Resource {
	private String name="";
	private String description="";
	private String tags="";
	private String uri="";
	private String channel="";
	private String owner="";
	private String ezserver=null;
		
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getTags() {
		return tags;
	}
	public void setTags(String tags) {
		this.tags = tags;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
		
	}
	public String getChannel() {
		return channel;
	}
	public void setChannel(String channel) {
		this.channel = channel;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public String getEzserver() {
		return ezserver;
	}
	public void setEzserver(String ezserver) {
		this.ezserver = ezserver;
	}
	
	/**
	 * transform the resource to JSON format
	 * @return resource in JSONObject format
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject(){
		JSONObject resJSON = new JSONObject();
		resJSON.put("name", this.name);
		
		JSONArray tagsArray = new JSONArray();
		if(!this.tags.equals("")){
			String[] tagsList = this.tags.split(",");
			for(int i = 0; i<tagsList.length; i++){
				tagsArray.add(i, tagsList[i]);
			}
			resJSON.put("tags", tagsArray);
		}
		else{
			resJSON.put("tags", tagsArray);
		}
		
		resJSON.put("description", this.description);
		resJSON.put("uri", this.uri);
		resJSON.put("channel", this.channel);
		resJSON.put("owner", this.owner);
		resJSON.put("ezserver", this.ezserver);
		return resJSON;
	}

	
}

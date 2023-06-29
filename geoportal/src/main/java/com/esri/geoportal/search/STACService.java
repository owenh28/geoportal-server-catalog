/* See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Esri Inc. licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.search;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;

import com.esri.geoportal.base.util.JsonUtil;
import com.esri.geoportal.base.util.ResourcePath;
import com.esri.geoportal.context.AppResponse;
import com.esri.geoportal.context.GeoportalContext;
import com.esri.geoportal.lib.elastic.ElasticContext;
import com.esri.geoportal.lib.elastic.http.ElasticClient;
import com.esri.geoportal.lib.elastic.http.request.GetItemRequest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

/**
 * STAC API: Records service provider.
 */
@ApplicationPath("stac")
@Path("")
public class STACService extends Application {

	/** Logger. */
	private static final Logger LOGGER = LoggerFactory.getLogger(STACService.class);

	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> resources = new HashSet<Class<?>>();
		resources.add(STACService.class);
		return resources;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get(@Context HttpServletRequest hsr) {
		String response = null;
		Status status = Response.Status.OK;
		try {
			response = this.readResourceFile("service/config/stac-description.json", hsr);

		} catch (Exception e) {
			LOGGER.error("Error in conformance " + e);
			status = Response.Status.INTERNAL_SERVER_ERROR;
			response = ("{\"error\":\"STAC API Landing Page could not be generated.\"}");
		}
		return Response.status(status).entity(response).build();
	}

	@GET
	@Path("/conformance")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getConformance(@Context HttpServletRequest hsr) {
		String responseJSON = null;
		Status status = Response.Status.OK;
		try {
			responseJSON = this.readResourceFile("service/config/stac-conformance.json", hsr);

		} catch (Exception e) {
			LOGGER.error("Error in conformance " + e);
			status = Response.Status.INTERNAL_SERVER_ERROR;

			responseJSON = ("{\"error\":\"STAC API Conformance response could not be generated.\"}");

		}
		return Response.status(status).entity(responseJSON).build();

	}

	@GET
	@Path("/api")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getApi(@Context HttpServletRequest hsr) {
		String responseJSON = null;
		Status status = Response.Status.OK;
		try {
			responseJSON = this.readResourceFile("service/config/stac-api.json", hsr);
		} catch (Exception e) {
			LOGGER.error("Error in conformance " + e);
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API description response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}

	@GET
	@Path("/collections")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCollections(@Context HttpServletRequest hsr) {
		String responseJSON = null;
		Status status = Response.Status.OK;
		try {
			responseJSON = this.readResourceFile("service/config/stac-collections.json", hsr);

		} catch (Exception e) {
			LOGGER.error("Error in conformance " + e);
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API collection response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}

	@GET
	@Path("/collections/metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCollectionMetadata(@Context HttpServletRequest hsr) {
		String responseJSON = null;
		Status status = Response.Status.OK;
		try {
			responseJSON = this.readResourceFile("service/config/stac-collection-metadata.json", hsr);

		} catch (Exception e) {
			LOGGER.error("Error in conformance " + e);
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API collection response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections/metadata/items")
	public Response getItems(@Context HttpServletRequest hsr, @QueryParam("limit") int limit,
			@QueryParam("bbox") String bbox, @QueryParam("datetime") String datetime, @QueryParam("from") int from)
			throws UnsupportedEncodingException {
		String responseJSON = null;
		String response = "";
		Status status = Response.Status.OK;
		limit = setLimit(limit);

		String query = "";

		try {
			ElasticContext ec = GeoportalContext.getInstance().getElasticContext();
			ElasticClient client = ElasticClient.newClient();
			String url = client.getTypeUrlForSearch(ec.getIndexName());
			Map<String, String> queryMap = new HashMap<String, String>();

			if (bbox != null && bbox.length() > 0)
				queryMap.put("bbox", bbox);
			if (datetime != null && datetime.length() > 0)
				queryMap.put("datetime", datetime);

			if (from > 0) {
				url = url + "/_search?size=" + limit + "&from=" + from;
			} else
				url = url + "/_search?size=" + limit;

			query = this.prepareMustQuery(queryMap);			
			if (query.length() > 0)
				response = client.sendPost(url, query, "application/json");
			else
				response = client.sendGet(url);

			responseJSON = this.prepareResponse(response, hsr, bbox, from, limit, datetime,null,null);

		} catch (Exception e) {
			LOGGER.error("Error in getting items " + e.getCause());
			e.printStackTrace();
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API Collection metadata items response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/search")
	public Response search(@Context HttpServletRequest hsr, @QueryParam("limit") int limit,
			@QueryParam("bbox") String bbox,
			@QueryParam("intersects") String intersects, 
			@QueryParam("datetime") String datetime, 
			@QueryParam("ids") String idList,
			@QueryParam("from") int from)
			throws UnsupportedEncodingException {
		String responseJSON = null;
		String response = "";
		Status status = Response.Status.OK;
		limit = setLimit(limit);
		
		String query = "";

		try {
			ElasticContext ec = GeoportalContext.getInstance().getElasticContext();
			ElasticClient client = ElasticClient.newClient();
			String url = client.getTypeUrlForSearch(ec.getIndexName());
			Map<String, String> queryMap = new HashMap<String, String>();

			if (bbox != null && bbox.length() > 0)
				queryMap.put("bbox", bbox);
			if (datetime != null && datetime.length() > 0)
				queryMap.put("datetime", datetime);
			
			if(idList != null && idList.length() >0)
			{
				//"LC80100252015082LGN00,LC80100252014287LGN00"
				if(idList.indexOf("[")<0)
					queryMap.put("ids", idList);				
			}
			
			if(intersects != null && intersects.length() >0)
				queryMap.put("intersects", intersects);

			if (from > 0) {
				url = url + "/_search?size=" + limit + "&from=" + from;
			} else
				url = url + "/_search?size=" + limit;

			query = this.prepareMustQuery(queryMap);
			System.out.println("final query "+query);
			if (query.length() > 0)
				response = client.sendPost(url, query, "application/json");
			else
				response = client.sendGet(url);

			responseJSON = this.prepareResponse(response, hsr, bbox, from, limit, datetime,idList,intersects);

		} catch (Exception e) {
			LOGGER.error("Error in getting items " + e.getCause());
			e.printStackTrace();
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API Collection metadata items response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/search")
	@Consumes({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN,MediaType.WILDCARD})
	public Response search(@Context HttpServletRequest hsr,@RequestBody String body)
			throws UnsupportedEncodingException {
		String responseJSON = null;
		String response = "";
		Status status = Response.Status.OK;
		System.out.println(body);
		JsonObject requestPayload = (JsonObject) JsonUtil.toJsonStructure(body);
		
		int limit = (requestPayload.containsKey("limit") ? requestPayload.getInt("limit"): 0);
		limit = setLimit(limit);		
		int from = (requestPayload.containsKey("from") ? requestPayload.getInt("from"): -1);
		String datetime = (requestPayload.containsKey("datetime") ? requestPayload.getString("datetime"): null);
		
		JsonArray bboxJsonArr = (requestPayload.containsKey("bbox") ? requestPayload.getJsonArray("bbox"): null);			
		JsonArray idArr= (requestPayload.containsKey("ids") ? requestPayload.getJsonArray("ids"): null);	
				
		JsonObject intersects = (requestPayload.containsKey("intersects") ? requestPayload.getJsonObject("intersects"): null);		
		
		String query = "";
		String bbox="";
		String ids="";

		try {
			ElasticContext ec = GeoportalContext.getInstance().getElasticContext();
			ElasticClient client = ElasticClient.newClient();
			String url = client.getTypeUrlForSearch(ec.getIndexName());
			Map<String, String> queryMap = new HashMap<String, String>();

			if (bboxJsonArr != null && bboxJsonArr.size() > 0)
			{
				
				for(int i=0;i<bboxJsonArr.size();i++)
				{
					if(i>0)
						bbox = bbox+","+bboxJsonArr.get(i);
					else
						bbox = bbox+bboxJsonArr.get(i);
				}
				queryMap.put("bbox", bbox);
			}
				
			if (datetime != null && datetime.length() > 0)
			{
				queryMap.put("datetime", datetime);
			}
				
			
			if(idArr != null && idArr.size() >0)
			{			
				//["LC80100252015082LGN00","LC80100252014287LGN00"] 				
				for(int i=0; i<idArr.size();i++)
				{
					if(i>0)
						ids = ids+","+idArr.getString(i);
					else
						ids = ids+idArr.getString(i);
				}				
				queryMap.put("ids", "\""+ids+"\"");
			}
			
			if(intersects != null && !intersects.isEmpty())
			{
				queryMap.put("intersects", intersects.toString());
			}
				

			if (from > 0) {
				url = url + "/_search?size=" + limit + "&from=" + from;
			} else
			{
				url = url + "/_search?size=" + limit;
			}
				

			query = this.prepareMustQuery(queryMap);
			System.out.println("final query "+query);
			if (query.length() > 0)
				response = client.sendPost(url, query, "application/json");
			else
				response = client.sendGet(url);

			responseJSON = this.prepareResponse(response, hsr, bbox, from, limit, datetime,ids,intersects.toString());

		} catch (Exception e) {
			LOGGER.error("Error in getting items " + e.getCause());
			e.printStackTrace();
			status = Response.Status.INTERNAL_SERVER_ERROR;
			responseJSON = ("{\"error\":\"STAC API Collection metadata items response could not be generated.\"}");
		}
		return Response.status(status).entity(responseJSON).build();
	}

	
	private String prepareResponse(String searchRes, HttpServletRequest hsr, String bbox, int from, int limit,
			String datetime,String ids, String intersects) {
		int numberMatched;
		net.minidev.json.JSONArray items = null;
	
		String numberReturned = "";
		String itemFileString = "";		
		String finalResponse = "";
		try {
			itemFileString = this.readResourceFile("service/config/stac-items.json", hsr);

			DocumentContext elasticResContext = JsonPath.parse(searchRes);
			DocumentContext resourceFilecontext = JsonPath.parse(itemFileString);

			JsonObject fileObj = (JsonObject) JsonUtil.toJsonStructure(itemFileString);
			String featureTemplateStr = fileObj.getJsonObject("featurePropPath").toString();
			featureTemplateStr = "{\"featurePropPath\":" + featureTemplateStr + "}";

			numberMatched = elasticResContext.read("$.hits.total.value");
			items = elasticResContext.read("$.hits.hits");
			numberReturned = String.valueOf(items.size());

			resourceFilecontext.set("$.response.timestamp", new Date().toString()).jsonString();
			resourceFilecontext.set("$.response.numberMatched", "" + numberMatched);
			resourceFilecontext.set("$.response.numberReturned", "" + numberReturned);
			resourceFilecontext.set("$.response.start", "" + from);
			
			JSONArray jsonArray = new JSONArray();
			
			for (int i = 0; i < items.size(); i++) {
				DocumentContext featureContext = JsonPath.parse(featureTemplateStr);
				DocumentContext searchItemCtx = JsonPath.parse(items.get(i));

				String val = featureContext.read("$.featurePropPath.id");
				featureContext.set("$.featurePropPath.id", searchItemCtx.read(val));

				val = featureContext.read("$.featurePropPath.collection");
				featureContext.set("$.featurePropPath.collection", searchItemCtx.read(val));

				val = featureContext.read("$.featurePropPath.assets.href");
				featureContext.set("$.featurePropPath.assets.href", searchItemCtx.read(val));

				val = featureContext.read("$.featurePropPath.assets.title");
				featureContext.set("$.featurePropPath.assets.title", searchItemCtx.read(val));
				
				//add bbox, geometry
				val = featureContext.read("$.featurePropPath.geometry");
				JSONArray enveloperArr = searchItemCtx.read(val);
				HashMap hm = (HashMap) enveloperArr.get(0);
				
				JSONArray geomArr = (JSONArray) hm.get("coordinates");
				JSONArray geomArr0 = (JSONArray) geomArr.get(0);
				JSONArray geomArr1 = (JSONArray) geomArr.get(1);				
				LOGGER.trace("Items done "+i);
				
				JSONArray coordArr0= new JSONArray();
				JSONArray coordArr1= new JSONArray();
				JSONArray coordArr2= new JSONArray();
				JSONArray coordArr3= new JSONArray();
				JSONArray coordArr4= new JSONArray();
				
				JSONArray finalCoordinateArr = new JSONArray();
				
				JSONObject geomObj = new JSONObject();
				geomObj.put("type", "Polygon");
				
				Double xmin = Double.parseDouble(geomArr0.get(0).toString());
				Double ymax = Double.parseDouble(geomArr0.get(1).toString());				
						
				Double xmax = Double.parseDouble(geomArr1.get(0).toString());
				Double ymin = Double.parseDouble(geomArr1.get(1).toString());
				
				coordArr0.add(xmin);
				coordArr0.add(ymin);				
				finalCoordinateArr.add(coordArr0);
				
				coordArr1.add(xmin);
				coordArr1.add(ymax);				
				finalCoordinateArr.add(coordArr1);				
				
				coordArr2.add(xmax);
				coordArr2.add(ymax);				
				finalCoordinateArr.add(coordArr2);
								
				coordArr3.add(xmax);
				coordArr3.add(ymin);				
				finalCoordinateArr.add(coordArr3);
				
				coordArr4.add(xmin);
				coordArr4.add(ymin);				
				finalCoordinateArr.add(coordArr4);			
				
				geomObj.put("coordinates", finalCoordinateArr);
				featureContext.set("$.featurePropPath.geometry", geomObj);	
							
				JSONArray arr = new JSONArray();
				arr.add(xmin);
				arr.add(ymin);
				arr.add(xmax);
				arr.add(ymax);
				featureContext.set("$.featurePropPath.bbox", arr);	
				
				//Iterate properties in stac-items.json and populate values
				this.populateProperties(featureContext,searchItemCtx);			
				
				jsonArray.add(featureContext.read("$.featurePropPath"));					
				
			}
					
			resourceFilecontext.set("$.response.features", jsonArray);	
			//No next records then remove Next page
			
			
			JsonObject obj =(JsonObject) JsonUtil.toJsonStructure(resourceFilecontext.jsonString()); 
			JsonObject resObj =  obj.getJsonObject("response");
			 			
			finalResponse = resObj.toString();
			// Prepare urlparam for next page from=next&size=limit&bbox=bbox
			int next = from + limit;
			String urlparam = "from=" + next + "&limit=" + limit + "&bbox=" + bbox + "&datetime=" + datetime+(ids !=null ? "&ids="+ids :"");

			finalResponse = finalResponse.replaceAll("\\{urlparam\\}", "" + urlparam);
		} catch (IOException | URISyntaxException e) {
			LOGGER.error("Stac response could not be preapred. "+e.getMessage());
			e.printStackTrace();
		}
		return finalResponse;
	}


	private void populateProperties(DocumentContext featureContext, DocumentContext searchItemCtx) {		
		HashMap<String, String> propObj = featureContext.read("$.featurePropPath.properties");
		Set<String> propObjKeys = propObj.keySet();
		String propKeyVal = "";
		ArrayList<String> propToBeRemovedList = new ArrayList<String>();
		
		for (String propKey : propObjKeys)
		{
			try {
				propKeyVal = String.valueOf(propObj.get(propKey));
				//If it is a json path, set values from serach result
				if(propKeyVal.startsWith("$"))
				{
					if(searchItemCtx.read(propKeyVal) != null)
					{
						featureContext.set("$.featurePropPath.properties."+propKey, searchItemCtx.read(propKeyVal));
					}
				}
			}catch(Exception e)
				{
					//If json path not found or error in any property, remove this property in the end.
					//if removed here, concurrentModificationException
					propToBeRemovedList.add("$.featurePropPath.properties."+propKey);							
					LOGGER.trace("key: "+propKey+" could not be added. Reason : "+e.getMessage());
				}
			}
		for(String propToRemove: propToBeRemovedList)
		{
			featureContext.delete(propToRemove);	
		}		
	}
	

	private String prepareMustQuery(Map<String, String> queryMap) {
		String queryStr = "";
		JsonArrayBuilder builder = Json.createArrayBuilder();

		if (queryMap.containsKey("bbox")) {
			String bboxQry = this.prepareBbox((String) queryMap.get("bbox"));
			if (bboxQry.length() > 0)
				builder.add(JsonUtil.toJsonStructure(bboxQry));

		}
		if (queryMap.containsKey("datetime")) {
			String dateTimeQry = this.prepareDateTime(queryMap.get("datetime"));
			if (dateTimeQry.length() > 0)
				builder.add(JsonUtil.toJsonStructure(dateTimeQry));
		}
		if (queryMap.containsKey("ids")) {
			String idsQry = this.prepareIds(queryMap.get("ids"));
			if (idsQry.length() > 0)
				builder.add(JsonUtil.toJsonStructure(idsQry));
		}
		
		if (queryMap.containsKey("intersects")) {
			String intersectsQry = this.prepareIntersects(queryMap.get("intersects"));
			if (intersectsQry.length() > 0)
				builder.add(JsonUtil.toJsonStructure(intersectsQry));
		}
		
		JsonArray filter = builder.build();
		if (filter.size() > 0)
			queryStr = "{\"query\":{\"bool\": {\"must\":" + JsonUtil.toJson(filter) + "}}}";
		return queryStr;
	}

	private String prepareIntersects(String geoJson) {
		String query =""; 
		String field = "shape_geo";
		String spatialType = "geo_shape"; 
		String relation = "intersects";
		JsonObject obj = (JsonObject) JsonUtil.toJsonStructure(geoJson);		
		
		query = "{\"" + spatialType + "\": {\"" + field + "\": {\"shape\": {\"type\": \""+obj.getString("type")+"\","
				+ "\"coordinates\":"+ obj.get("coordinates")
				+ "},\"relation\": \"" + relation + "\"}}}";
		return query;
	}

	private String prepareIds(String ids) {
		return "{\"match\": {\"id\": "+ids+"}}";	
	}

	private String prepareDateTime(String datetime) {
		String query = "";
		String dateTimeFld = "sys_modified_dt";
		String dateTimeFldQuery = "";
		// Find from and to dates
		// https://api.stacspec.org/v1.0.0/ogcapi-features/#tag/Features/operation/getFeatures
//	Either a date-time or an interval, open or closed. Date and time expressions adhere to RFC 3339. Open intervals are expressed using double-dots.
//	Examples:
//	A date-time: "2018-02-12T23:20:50Z"
//	A closed interval: "2018-02-12T00:00:00Z/2018-03-18T12:31:12Z"
//	Open intervals: "2018-02-12T00:00:00Z/.." or "../2018-03-18T12:31:12Z"

		String fromField = datetime;
		String toField = "";
		List<String> dateFlds = Arrays.asList(datetime.split("/"));

		if (dateFlds.size() > 1) {
			fromField = dateFlds.get(0);
			toField = dateFlds.get(1);
		}
		if (toField.equals("") || toField.equals("..")) {
			dateTimeFldQuery = "{\"gte\": \"" + fromField + "\"}";
		} else if (fromField.equals("..")) {
			dateTimeFldQuery = "{\"lte\":\"" + toField + "\"}";
		} else {
			dateTimeFldQuery = "{\"gte\": \"" + fromField + "\",\"lte\":\"" + toField + "\"}";
		}

		query = "{\"range\": {\"" + dateTimeFld + "\":" + dateTimeFldQuery + "}}";

		return query;
	}

	private String prepareBbox(String bboxString) {
		String field = "envelope_geo";
		String spatialType = "geo_shape"; // geo_shape or geo_point
		String relation = "intersects";
		List<String> bbox = Arrays.asList(bboxString.split(",", -1));

		double coords[] = { -180.0, -90.0, 180.0, 90.0 };
		String query = "";
		if (bbox.size() > 3) {
			if ((Double.parseDouble(bbox.get(0)) < -180.0) && (Double.parseDouble(bbox.get(2)) >= -180.0))
				coords[0] = -180.0;
			else
				coords[0] = Double.parseDouble(bbox.get(0));
			if ((Double.parseDouble(bbox.get(1)) < -90.0) && (Double.parseDouble(bbox.get(3)) >= -90.0))
				coords[1] = -90.0;
			else
				coords[1] = Double.parseDouble(bbox.get(1));
			if ((Double.parseDouble(bbox.get(2)) > 180.0) && (Double.parseDouble(bbox.get(0)) <= 180.0))
				coords[2] = 180.0;
			else
				coords[2] = Double.parseDouble(bbox.get(2));
			if ((Double.parseDouble(bbox.get(3)) > 90.0) && (Double.parseDouble(bbox.get(1)) <= 90.0))
				coords[3] = 90.0;
			else
				coords[3] = Double.parseDouble(bbox.get(3));
		}

		if (coords.length > 3) {
			query = "{\"" + spatialType + "\": {\"" + field + "\": {\"shape\": {\"type\": \"envelope\","
					+ "\"coordinates\": [[" + coords[0] + "," + coords[3] + "], [" + coords[2] + "," + coords[1] + "]]"
					+ "},\"relation\": \"" + relation + "\"}}}";
		}
		return query;
	}

	private int setLimit(int limit) {
		if (limit == 0 || limit > 10000) {
			limit = 10; // default
		}
		return limit;
	}

	@GET
	@Path("collections/metadata/items/{featureId}")
	public Response getItem(@Context HttpServletRequest hsr, @PathParam("featureId") String id) {
		// To test 92e7716e2865405fb94ed14585649d0f
		GetItemRequest request = GeoportalContext.getInstance().getBean("request.GetItemRequest", GetItemRequest.class);
		request.init(id, null, false);
		try {
			AppResponse response = request.executeNOAuth();
			return response.build();
		} catch (Exception ex) {
			LOGGER.error("Error in get item " + ex.getCause());

			String responseJSON = ("{\"error\":\"STAC API feature response could not be generated.\"}");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseJSON).build();
		}
	}

	public String getBaseUrl(HttpServletRequest hsr) {

		StringBuffer requestURL = hsr.getRequestURL();
		String ctxPath = hsr.getContextPath();
		String baseUrl = requestURL.substring(0, requestURL.indexOf(ctxPath) + ctxPath.length());
		return baseUrl + "/stac";
	}

	public String readResourceFile(String path, HttpServletRequest hsr) throws IOException, URISyntaxException {
		ResourcePath rp = new ResourcePath();
		URI uri = rp.makeUrl(path).toURI();
		String filedataString = new String(Files.readAllBytes(Paths.get(uri)), "UTF-8");

		if (filedataString != null)
			filedataString = filedataString.trim();
		String requestURL = hsr.getRequestURL().toString();
		// Remove last /
		requestURL = requestURL.substring(0, requestURL.length() - 1);

		// Replace {url}
		filedataString = filedataString.replaceAll("\\{url\\}", this.getBaseUrl(hsr));
		return filedataString;
	}

}

package com.example.controllers;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.ClosestPoint;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.log4j.Log4j2;
import net.sf.json.*;
import net.sf.json.xml.XMLSerializer;

@Log4j2
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
@RestController
public class LocationController {

    final private String CWB_KEY = "CWB-6BD4A26E-3011-4BEC-B229-706658ADBBFF";

    final private String STATIONS_URL = "https://opendata.cwb.gov.tw/api/v1/rest/datastore/C-B0074-002?Authorization=rdec-key-123-45678-011121314";

    /**
     * 氣象站條件( id、name ) 篩選不作用
     * @see https://e-service.cwb.gov.tw/wdps/obs/state.htm
     * 預報因子中文對應
     * @see https://opendata.cwb.gov.tw/opendatadoc/DIV2/A0003-001.pdf
     */
    final private String OBS_URL = "https://opendata.cwb.gov.tw/api/v1/rest/datastore/O-A0003-001";

    /**
     * 單點坐標回傳行政區(戶政)
     * @see https://data.gov.tw/dataset/152915 
    */
    final private String LATLNG_QUERY_URL = "https://api.nlsc.gov.tw/other/TownVillagePointQuery1/{lng}/{lat}/4326";
    
    /**
     * 代碼服務－縣市清單
     * @see https://data.gov.tw/dataset/101905 
     */
    final private String COUNTY_URL = "https://api.nlsc.gov.tw/other/ListCounty";

    /**
     * 代碼服務－鄉鎮市區清單(地政)
     * @see https://data.gov.tw/dataset/102013 
     */
    final private String TOWNSHIP_URL = "https://api.nlsc.gov.tw/other/ListTown/{code}";
    
    /**
     * 代碼服務－村里清單
     * @see https://data.gov.tw/dataset/102017
     */
    final private String VILLAGE_URL = "https://api.nlsc.gov.tw/other/ListVillage/{ctyCode}/{townCode}";
    
    @Autowired
    private RestTemplate restTemplate;

    // private HttpServletRequest request;

    private ArrayList<Object> stations = new ArrayList<>();
    private ArrayList<double[]> stationLocations = new ArrayList<>();
    
    private Map<String,String> codeCountryMap = new HashMap<>();

    
    @GetMapping("/countyAndTownship")
    @Operation(summary = "依經緯度取得所在縣市、鄉鎮、里")
    public ResponseEntity<?> queryByLatLng (
        @RequestParam(name = "lng", required = true) String lng,
        @RequestParam(name = "lat", required = true) String lat
    ){
        try{

            ResponseEntity<String> response = restTemplate.exchange(
                LATLNG_QUERY_URL.replace("{lng}",lng ).replace("{lat}",lat),
                HttpMethod.GET,
                null,
                String.class
            );
    
            return ResponseEntity.ok(response.getBody());
        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    }

    @GetMapping("/county")
    @Operation(summary = "取得所有縣市")
    public ResponseEntity<?> getAllCounty (){
        try{
            if(codeCountryMap.size()==0){
                ResponseEntity<String> response = restTemplate.exchange(
                    COUNTY_URL,
                    HttpMethod.GET,
                    null,
                    String.class
                );


                XMLSerializer xmlSerializer = new XMLSerializer();
                JSON json = xmlSerializer.read(response.getBody());
  
                JSONArray jsonArr = JSONArray.fromObject(json.toString());
                for (Object object : jsonArr) {    
                    String countycode = JSONObject.fromObject(object).getString("countycode");
                    String countyname = JSONObject.fromObject(object).getString("countyname");
                    codeCountryMap.put(countycode, countyname);
                }

            }

            ArrayList<Map<String,String>> result = new ArrayList<>();
            for (String key : codeCountryMap.keySet()) {
                result.add(Map.of(
                    "code", key,
                    "value", codeCountryMap.get(key)
                ));
            }
            
            return ResponseEntity.ok(result);

        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    
    }

   
    @GetMapping("/county/{code}/township")
    @Operation(summary = "依縣市代碼取得行政區")
    public ResponseEntity<?> getTownshipByCountryCityCode (@PathVariable("code") String code){
        try{
            ResponseEntity<String> response = restTemplate.exchange(
                TOWNSHIP_URL.replace("{code}", code),
                HttpMethod.GET,
                null,
                String.class
            );

            
            XMLSerializer xmlSerializer = new XMLSerializer();
            JSON json = xmlSerializer.read(response.getBody());
            JSONArray jsonArr = JSONArray.fromObject(json);
            
            ArrayList<Map<String,String>> result = new ArrayList<>();
            for (Object obj : jsonArr) {
                result.add(Map.of(
                    "code", JSONObject.fromObject(obj).getString("towncode"),
                    "value",JSONObject.fromObject(obj).getString("townname")
                ));
            }

            return ResponseEntity.ok(result);
        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }    
    }
    
    
    @GetMapping("/county/{ctyCode}/township/{townCode}")
    @Operation(summary = "依縣市代碼及鄉鎮代碼取得村里")
    public ResponseEntity<?> getVillages(
        @PathVariable("ctyCode") String ctyCode,
        @PathVariable("townCode") String townCode
    ){
        try{
            ResponseEntity<String> response = restTemplate.exchange(
                VILLAGE_URL.replace("{ctyCode}", ctyCode).replace("{townCode}", townCode),
                HttpMethod.GET,
                null,
                String.class
            );

            
            XMLSerializer xmlSerializer = new XMLSerializer();
            JSON json = xmlSerializer.read(response.getBody());
            JSONArray jsonArr = JSONArray.fromObject(json);
            
            ArrayList<Map<String,String>> result = new ArrayList<>();
            for (Object obj : jsonArr) {
                result.add(Map.of(
                    "code", JSONObject.fromObject(obj).getString("villageId"),
                    "value",JSONObject.fromObject(obj).getString("villageName")
                ));
            }

            return ResponseEntity.ok(result);
        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }    
    }

    @Deprecated
    @GetMapping("/weather/station")
    @Operation(summary = "依給定經緯度取得距離最近的氣象觀測站 ( CWB API 參數 stationID、stationName 無法篩選 )")
    public ResponseEntity<?> getClosetStation (
        @RequestParam(name = "lng", required = true) String lng,
        @RequestParam(name = "lat", required = true) String lat
    ) {
        try{
            ResponseEntity<String> response = restTemplate.exchange(
                STATIONS_URL,
                HttpMethod.GET,
                null,
                String.class
            );
    
            JSONArray _stations = JSONObject.fromObject(response.getBody())
                .getJSONObject("records")
                .getJSONObject("data")
                .getJSONObject("stationStatus")
                .getJSONArray("station");


            if(stations.size() == 0){
                for (Object _station : _stations) {
                    double _lng = Double.parseDouble(JSONObject.fromObject(_station).get("longitude").toString());
                    double _lat = Double.parseDouble(JSONObject.fromObject(_station).get("latitude").toString());
                    
                    stations.add(_station);
                    double[] coord = {_lat, _lng};
                    stationLocations.add(coord);
                }
            }

            double[][] points = stationLocations.toArray(new double[stationLocations.size()][]);
            double[] latLng = { Double.parseDouble(lat),Double.parseDouble(lng) };
            
            Integer idx = ClosestPoint.nearestPoint(latLng, points);
            
            log.info("stations[idx] {}" , stations.get(idx));
            
            return ResponseEntity.ok(stations.get(idx));
        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    }

    @GetMapping("/weather")
    @Operation(summary = "依給定經緯度取得距離最近的氣象觀測站的最新觀測資料")
    public ResponseEntity<?> getObsWeather (
        @RequestParam(name = "lng", required = true) String lng,
        @RequestParam(name = "lat", required = true) String lat
    ){
        try{
            String url = UriComponentsBuilder.fromHttpUrl(OBS_URL)
                .queryParam("Authorization", CWB_KEY)
                .toUriString();

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                String.class
            );

            JSONArray locations = JSONObject.fromObject(response.getBody())
                .getJSONObject("records")
                .getJSONArray("location");

            ArrayList<double[]> points = new ArrayList<>();

            for (Object location : locations) {
                double _lng = Double.parseDouble(JSONObject.fromObject(location).get("lon").toString());
                double _lat = Double.parseDouble(JSONObject.fromObject(location).get("lat").toString());
                points.add(new double[] {_lat, _lng});
            }
            Integer idx = ClosestPoint.nearestPoint(
                new double[] { Double.parseDouble(lat),Double.parseDouble(lng) }, 
                points.toArray(new double[points.size()][])
            );

            return ResponseEntity.ok(locations.get(idx));
        }catch(Exception e){
            // e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }    
    }

    @GetMapping("/proxy")
    @Operation(summary = "代理 GET 方法")
    public ResponseEntity<?> proxyUrl(
        @RequestParam(name = "url", required = true) String url,
        @RequestParam Map<String,String> params
    ) {
        try{
            LinkedMultiValueMap<String, String> multiValueMap= new LinkedMultiValueMap<>();
            multiValueMap.setAll(params);
    
            String _url = UriComponentsBuilder.fromHttpUrl(url)
                .queryParams(multiValueMap)
                .toUriString();
    
            return restTemplate.exchange(
                _url,
                HttpMethod.GET,
                null,
                String.class
            );
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    }
}

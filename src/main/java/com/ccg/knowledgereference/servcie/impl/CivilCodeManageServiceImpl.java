package com.ccg.knowledgereference.servcie.impl;

import com.alibaba.fastjson.JSONObject;
import com.ccg.knowledgereference.com.ccg.controller.com.ccg.ConvertUtil;
import com.ccg.knowledgereference.com.ccg.controller.constant.CivilCodeUnitEnum;
import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeItem;
import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeNode;
import com.ccg.knowledgereference.servcie.CivilCodeManageService;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.boot.jackson.JsonObjectDeserializer;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ccg.knowledgereference.com.ccg.controller.constant.CivilCodeUnitEnum.*;

@Service
@Slf4j
public class CivilCodeManageServiceImpl implements CivilCodeManageService {

    /**
     * 分编 仅用于划分章， 但章依然属于编  每一新编 会重新从1开始  分编仅第2 3编存在。
     * 章  每一新编 会重新从1开始 但会分编划分成很多块
     * 节  第一新章会重新从1开始
     * 条  最基本的单元 只会从1依次增加 一共1258 + 2条 具体内容 上面则是主题
     * <p>
     * 特殊内容 条内容中会存在引用 第几条 节等、 第一条存在多行内容情况 （一） （二）点  章下面存在没有节 直接到条
     *
     * @param path
     * @return
     */
    @Override
    public boolean importData(String path) {
        File file = new File(path);
        try (BufferedReader bfr = new BufferedReader(new FileReader(file))) {
            // 1300 总条数
            List<CivilCodeItem> items = new ArrayList<>(1270);
            String lineStr;

            CivilCodeNode codification =  new CivilCodeNode(), subCodification =  new CivilCodeNode(),
                    chapter =  new CivilCodeNode(), section =  new CivilCodeNode(), item = new CivilCodeNode();
            CivilCodeItem newItem;
            int lastIndex = -1;
            while ((lineStr = bfr.readLine()) != null) {
                lineStr = lineStr.trim();
                if (lineStr == null || "".equals(lineStr)) {
                    continue;
                }
//                log.info("行内容：{}",lineStr);
                String tile = this.getTile(lineStr);
                if (tile != null) {
                    CivilCodeUnitEnum codeUnitEnum = this.getUnitByTile(tile);
                    CivilCodeNode civilCodeNode = new CivilCodeNode();
                    civilCodeNode.setNum(this.getNumByTile(tile));
                    // todo 考虑是否同时需要这两者
                    //civilCodeNode.setContent(this.getContent(lineStr, tile.length()));
                    civilCodeNode.setOriginalValue(lineStr);
                    switch (codeUnitEnum) {
                        case CODIFICATION:
                            /**
                             * 分编 仅用于划分章， 但章依然属于编  每一新编 会重新从1开始  分编仅第2 3编存在。
                             * 章  每一新编 会重新从1开始 但会分编划分成很多块
                             */
                            subCodification = null;
                            chapter = null;
                            civilCodeNode.setLevelUnit(CODIFICATION);
                            codification = civilCodeNode;
                            break;
                        case SUB_CODIFICATION:
                            civilCodeNode.setLevelUnit(SUB_CODIFICATION);
                            subCodification = civilCodeNode;
                            break;
                        case CHAPTER:
                            // 节  第一新章会重新从1开始
                            // 有些章下面直接是条 需要置null
                            section = null;
                            civilCodeNode.setLevelUnit(CHAPTER);
                            chapter = civilCodeNode;
                            break;
                        case SECTION:
                            civilCodeNode.setLevelUnit(SECTION);
                            section = civilCodeNode;
                            break;
                        case ITEM:
                            civilCodeNode.setLevelUnit(ITEM);
                            item = civilCodeNode;
                            newItem = new CivilCodeItem();
                            newItem.setCodification(codification);
                            newItem.setSubCodification(subCodification);
                            newItem.setChapter(chapter);
                            newItem.setSection(section);
                            newItem.setItem(item);

                            items.add(newItem);
                            lastIndex++;
                            break;
                        default:
                            log.error("pare civil node error");
                    }
                }else{
                    // 存在条文是多行的
                    //String lastContent = items.get(lastIndex).getItem().getContent();
                    //items.get(lastIndex).getItem().setContent(lastContent + lineStr);

                    String lastOriginalValue = items.get(lastIndex).getItem().getOriginalValue();
                    items.get(lastIndex).getItem().setOriginalValue(lastOriginalValue + lineStr);
                }
            }
            this.write2Es(items);
        } catch (Exception e) {
            log.error("import civil code error ", e);
            return false;
        }

        return false;
    }

    private final static String CIVIL_CODE_INDEX =  "civil-code-index";
    private final static String ITEM_ORIGINAL_VALUE =  "item.originalValue";
    private final static String ITEM_NUM =  "item.num";

    @Override
    public List<CivilCodeItem> fullTextQuery(String content, Integer from, Integer size) throws IOException {
        RestHighLevelClient client = null;
        try {
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost("localhost", 9200, "http")));
            SearchRequest searchRequest = new SearchRequest(CIVIL_CODE_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            if(size != null && size > 0){
                searchSourceBuilder.size(size);
            }
            if (from != null) {
                searchSourceBuilder.from(from);
            }
            searchSourceBuilder.query(QueryBuilders.matchQuery(ITEM_ORIGINAL_VALUE, content));
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

            SearchHits hits = response.getHits();
            if (hits != null && hits.getHits() != null && hits.getHits().length > 0) {
                List<CivilCodeItem> result = new LinkedList<>();
                for (SearchHit hit : hits.getHits()) {
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    result.add(JSONObject.parseObject(JSONObject.toJSONString(sourceAsMap), CivilCodeItem.class));
                }
                return result;
            }
        } catch (Exception e) {
            log.error("full text query error ", e);
        } finally {
            client.close();
        }
        return null;
    }

    @Override
    public List<CivilCodeItem> rangeById(Integer start, Integer end) throws IOException {
        RestHighLevelClient client = null;
        try {
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost("localhost", 9200, "http")));
            SearchRequest searchRequest = new SearchRequest(CIVIL_CODE_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(ITEM_NUM).gte(start).lte(end);

            searchSourceBuilder.query(rangeQueryBuilder);
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

            SearchHits hits = response.getHits();
            if (hits != null && hits.getHits() != null && hits.getHits().length > 0) {
                List<CivilCodeItem> result = new LinkedList<>();
                for (SearchHit hit : hits.getHits()) {
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    result.add(JSONObject.parseObject(JSONObject.toJSONString(sourceAsMap), CivilCodeItem.class));
                }
                return result;
            }
        } catch (Exception e) {
            log.error("full text query error ", e);
        } finally {
            client.close();
        }
        return null;
    }

    private void write2Es(List<CivilCodeItem> items) throws IOException {
        RestHighLevelClient client = null;
        try {
            // TODO 复用client
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost("localhost", 9200, "http")));
            for (CivilCodeItem item : items) {
                IndexRequest request = new IndexRequest(CIVIL_CODE_INDEX);
                request.id(item.getItem().getNum() + "");
                String jsonStr = JSONObject.toJSONString(item);
                log.info("jsonStr: {}", jsonStr);
                request.source(jsonStr, XContentType.JSON);
                IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            }
        } catch (Exception e) {
            log.error("write to es error: ", e);
        } finally {
            client.close();
        }
    }

    /**
     * 得到 第几编 分编 章 节 条
     *
     * @param lineStr
     * @return
     */
    private String getTile(String lineStr) {
        Pattern pattern = Pattern.compile("第.{1,8}[\\p{Zs}]");
        Matcher matcher = pattern.matcher(lineStr);
        if (matcher.find()) {
            String s = matcher.group(0);
            return s;
        }
        return null;
    }

    private CivilCodeUnitEnum getUnitByTile(String tile) {
        if (tile.contains(SUB_CODIFICATION.getName())) {
            return SUB_CODIFICATION;
        }

        String name = tile.substring(tile.length() - 2, tile.length() - 1);
        return CivilCodeUnitEnum.find(name);
    }

    private String getContent(String lineStr, int index) {
        return lineStr.substring(index);
    }

    private int getNumByTile(String tile) {
        String numCn;
        if (tile.contains(SUB_CODIFICATION.getName())) {
            numCn = tile.substring(1, tile.length() - 3);
        } else {
            numCn = tile.substring(1, tile.length() - 2);
        }
        return ConvertUtil.covertToInt(numCn);
    }

    /**
     # 民法典 mapping
     PUT /civil-code-index?pretty
     {
     "mappings": {
     "properties": {
     "codification": {
     "properties": {
     "levelUnit": {
     "type": "integer"
     },
     "num": {
     "type": "integer"
     },
     "originalValue": {
     "type": "text",
     "analyzer": "ik_smart",
     "search_analyzer": "ik_smart"
     }
     }
     },
     "subCodification": {
     "properties": {
     "levelUnit": {
     "type": "integer"
     },
     "num": {
     "type": "integer"
     },
     "originalValue": {
     "type": "text",
     "analyzer": "ik_smart",
     "search_analyzer": "ik_smart"
     }
     }
     },
     "chapter": {
     "properties": {
     "levelUnit": {
     "type": "integer"
     },
     "num": {
     "type": "integer"
     },
     "originalValue": {
     "type": "text",
     "analyzer": "ik_smart",
     "search_analyzer": "ik_smart"
     }
     }
     },
     "secction": {
     "properties": {
     "levelUnit": {
     "type": "integer"
     },
     "num": {
     "type": "integer"
     },
     "originalValue": {
     "type": "text",
     "analyzer": "ik_smart",
     "search_analyzer": "ik_smart"
     }
     }
     },
     "item": {
     "properties": {
     "levelUnit": {
     "type": "integer"
     },
     "num": {
     "type": "integer"
     },
     "originalValue": {
     "type": "text",
     "analyzer": "ik_smart",
     "search_analyzer": "ik_smart"
     }
     }
     }
     }
     }
     }
     * @param args
     */

    public static void main(String[] args) {
        CivilCodeManageServiceImpl s = new CivilCodeManageServiceImpl();
        String line = "第三章　法人";
        line = "第一节　一般规定";
        line = "第一十一条　法人是具有民事权利能力和民事行为能力，依法独立享有民事权利和承担民事义务的组织。";

        String tile = s.getTile(line);
//        System.out.println(tile);
//        System.out.println(s.getUnitByTile(tile).getName());
//        System.out.println(s.getContent(line, tile.length()));
//        System.out.println(s.getNumByTile(tile));
    }
}

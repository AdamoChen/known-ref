package com.ccg.knowledgereference.servcie.impl;

import com.alibaba.fastjson.JSONObject;
import com.ccg.knowledgereference.com.ccg.controller.com.ccg.ConvertUtil;
import com.ccg.knowledgereference.com.ccg.controller.constant.CivilCodeUnitEnum;
import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeItem;
import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeNode;
import com.ccg.knowledgereference.config.EsConfig;
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
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.IntervalsSourceProvider;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private EsConfig esConfig;

    /**
     * ?????? ????????????????????? ?????????????????????  ???????????? ????????????1??????  ????????????2 3????????????
     * ???  ???????????? ????????????1?????? ??????????????????????????????
     * ???  ????????????????????????1??????
     * ???  ?????????????????? ?????????1???????????? ??????1258 + 2??? ???????????? ??????????????????
     * <p>
     * ???????????? ??????????????????????????? ????????? ????????? ????????????????????????????????? ????????? ????????????  ???????????????????????? ????????????
     *
     * @param path
     * @return
     */
    @Override
    public boolean importData(String path) {
        File file = new File(path);
        try (BufferedReader bfr = new BufferedReader(new FileReader(file))) {
            // 1300 ?????????
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
//                log.info("????????????{}",lineStr);
                String tile = this.getTile(lineStr);
                if (tile != null) {
                    CivilCodeUnitEnum codeUnitEnum = this.getUnitByTile(tile);
                    CivilCodeNode civilCodeNode = new CivilCodeNode();
                    civilCodeNode.setNum(this.getNumByTile(tile));
                    // todo ?????????????????????????????????
                    //civilCodeNode.setContent(this.getContent(lineStr, tile.length()));
                    civilCodeNode.setOriginalValue(lineStr);
                    switch (codeUnitEnum) {
                        case CODIFICATION:
                            /**
                             * ?????? ????????????????????? ?????????????????????  ???????????? ????????????1??????  ????????????2 3????????????
                             * ???  ???????????? ????????????1?????? ??????????????????????????????
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
                            // ???  ????????????????????????1??????
                            // ??????????????????????????? ?????????null
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
                    // ????????????????????????
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
        log.info("full text query: {} - {} - {}", content, from, size);
        RestHighLevelClient client = null;
        try {
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(esConfig.getHost(), esConfig.getPort(), "http")));
            SearchRequest searchRequest = new SearchRequest(CIVIL_CODE_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            if(size != null && size > 0){
                searchSourceBuilder.size(size);
            }
            if (from != null) {
                searchSourceBuilder.from(from);
            }
            // ??????
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field(ITEM_ORIGINAL_VALUE).highlighterType("plain");

            // match & ????????????
            MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(ITEM_ORIGINAL_VALUE, content)
                    .fuzziness(Fuzziness.AUTO);
            searchSourceBuilder.query(matchQueryBuilder)
                    .highlighter(highlightBuilder);
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

            SearchHits hits = response.getHits();
            if (hits != null && hits.getHits() != null && hits.getHits().length > 0) {
                List<CivilCodeItem> result = new LinkedList<>();
                for (SearchHit hit : hits.getHits()) {
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    CivilCodeItem civilCodeItem = JSONObject.parseObject(JSONObject.toJSONString(sourceAsMap), CivilCodeItem.class);
                    // ????????????????????? highlight ??????
                    Map<String, HighlightField> highlightFieldMap = hit.getHighlightFields();
                    HighlightField highlightField = highlightFieldMap.get(ITEM_ORIGINAL_VALUE);
                    String highlightContent = null;
                    if (highlightField.getFragments() != null && highlightField.getFragments().length > 0) {
                        highlightContent = highlightField.getFragments()[0].toString();
                    }
                    if (civilCodeItem.getItem() != null) {
                        civilCodeItem.getItem().setOriginalValue(highlightContent);
                    }
                    result.add(civilCodeItem);
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
                    RestClient.builder(new HttpHost(esConfig.getHost(), esConfig.getPort(), "http")));
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
            // TODO ??????client
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(esConfig.getHost(), esConfig.getPort(), "http")));
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
     * ?????? ????????? ?????? ??? ??? ???
     *
     * @param lineStr
     * @return
     */
    private String getTile(String lineStr) {
        Pattern pattern = Pattern.compile("???.{1,8}[\\p{Zs}]");
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
     # ????????? mapping
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
        String line = "??????????????????";
        line = "????????????????????????";
        line = "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";

        String tile = s.getTile(line);
//        System.out.println(tile);
//        System.out.println(s.getUnitByTile(tile).getName());
//        System.out.println(s.getContent(line, tile.length()));
//        System.out.println(s.getNumByTile(tile));
    }
}

package com.cunjunwang.shanghai.transportation.service.queryservice;

import com.cunjunwang.shanghai.transportation.constant.Constant;
import com.cunjunwang.shanghai.transportation.constant.ErrConstant;
import com.cunjunwang.shanghai.transportation.constant.ErrMsgConstant;
import com.cunjunwang.shanghai.transportation.exception.ShanghaiTransportationException;
import com.cunjunwang.shanghai.transportation.model.dto.*;
import com.cunjunwang.shanghai.transportation.model.vo.BusCurrentStopVO;
import com.cunjunwang.shanghai.transportation.model.vo.BusDetailVO;
import com.cunjunwang.shanghai.transportation.service.dataService.BusBaseDataService;
import com.cunjunwang.shanghai.transportation.service.dbservice.BusLineDBService;
import com.cunjunwang.shanghai.transportation.service.dbservice.BusStationDBService;
import com.cunjunwang.shanghai.transportation.util.HtmlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by CunjunWang on 2018-12-18.
 */
@Service
public class BusQueryService {

    private static final Logger logger = LoggerFactory.getLogger(BusQueryService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HtmlParserUtil htmlParserUtil;

    @Autowired
    private BusBaseDataService busBaseDataService;

    @Autowired
    private BusLineDBService busLineDBService;

    @Autowired
    private BusStationDBService busStationDBService;

    @Value("${com.cunjunwang.shanghai.bus.query.getStationsUrl}")
    private String getStationsURL;

    /**
     * 查询公交实时到站信息
     *
     * @param getBusCurrentStopDTO
     * @return
     */
    public BusCurrentStopVO queryBusCurrentStopInfo(GetBusCurrentStopDTO getBusCurrentStopDTO) {

        // 根据路线号获得Sid
        String busLineNumber = getBusCurrentStopDTO.getLineNumber();
        String sid = this.getBusSidByLineNumber(busLineNumber);
        logger.info("获取sid: {}", sid);
        // 封装参数
        GetBusStopDTO getBusStopDTO = new GetBusStopDTO();
        getBusStopDTO.setSid(sid);
        getBusStopDTO.setStopId(getBusCurrentStopDTO.getStopSequenceId());
        getBusStopDTO.setStopType(getBusCurrentStopDTO.getDirection());
        logger.info("请求实时公交信息参数: {}", getBusStopDTO.toString());

        BusCurrentStopVO busCurrentStopVO = busBaseDataService.getStop(getBusStopDTO);
        logger.info("请求结果: {}", busCurrentStopVO.toString());
        return busCurrentStopVO;
    }

    /**
     * 查询公交介绍信息
     *
     * @param busLineNumber
     * @return
     */
    public BusDetailVO queryBusDetail(String busLineNumber) {
        // 根据路线号获得Sid
        String sid = this.getBusSidByLineNumber(busLineNumber);
        logger.info("获取sid: {}", sid);
        GetBusStationsDTO getBusStationsDTO = new GetBusStationsDTO();
        getBusStationsDTO.setSid(sid);
        getBusStationsDTO.setStopType(Constant.UP_GOING);
        busBaseDataService.getBusStationsBySid(getBusStationsDTO);
        // 发送请求
        String fullUrl = String.format(getStationsURL, sid, Constant.UP_GOING);
        String responseHtml = restTemplate.getForObject(fullUrl, String.class);

        if (responseHtml != null && !StringUtils.isEmpty(responseHtml)) {
            BusDetailVO busDetailVO = htmlParserUtil.getBusIntroInfo(responseHtml);
            busDetailVO.setLineNum(busLineNumber);
            busDetailVO.setBusDirectionType(Constant.DOUBLE_DIRECTION);
            logger.info("响应参数: {}", busDetailVO.toString());
            return busDetailVO;
        } else {
            logger.error("网络请求错误");
            throw new ShanghaiTransportationException(ErrConstant.HTTP_REQUEST_ERR, ErrMsgConstant.HTTP_REQUEST_ERR_MSG);
        }
    }

    /**
     * 获取线路所有站点名
     * @param busLineNumber
     * @return
     */
    public List<String> getLineStationList(String busLineNumber) {
        if(busLineNumber == null) {
            logger.error("获取线路所有站点名，线路名无效");
            throw new ShanghaiTransportationException(ErrConstant.INVALID_PARAMETER, ErrMsgConstant.INVALID_PARAMETER_MSG);
        }
        logger.info("开始查询线路[{}]所有站点列表", busLineNumber);

        BusLineNumberDTO busLineNumberDTO = new BusLineNumberDTO();
        busLineNumberDTO.setIdnum(busLineNumber);
        BusSidDTO busSidDTO = busBaseDataService.getBusSID(busLineNumberDTO);
        String sid = busSidDTO.getSid();
        // 先请求上行站点信息
        GetBusStationsDTO getBusStationsDTO = new GetBusStationsDTO();
        getBusStationsDTO.setSid(sid);
        getBusStationsDTO.setStopType(Constant.UP_GOING);
        List<BusStationDTO> upGoingStationList = busBaseDataService.getBusStationsBySid(getBusStationsDTO);
        // 再请求下行站点信息
        getBusStationsDTO.setStopType(Constant.DOWN_GOING);
        List<BusStationDTO> downGoingStationList = busBaseDataService.getBusStationsBySid(getBusStationsDTO);
        // 去重
        Set<String> lineNumberSet = new HashSet<>();
        for(BusStationDTO busStationDTO : upGoingStationList) {
            lineNumberSet.add(busStationDTO.getStationName());
        }
        for(BusStationDTO busStationDTO : downGoingStationList) {
            lineNumberSet.add(busStationDTO.getStationName());
        }

        List<String> resultList = new ArrayList<>(lineNumberSet);
        logger.info("查询线路[{}]站点列表完成, 上下行共{{}]站, 信息: [{}]",
                busLineNumber, resultList.size(), resultList);

        return resultList;
    }

    /**
     * [Helper]
     * 根据线路查询Sid
     *
     * @param busLineNumber
     * @return
     */
    private String getBusSidByLineNumber(String busLineNumber) {
        BusLineNumberDTO busLineNumberDTO = new BusLineNumberDTO();
        busLineNumberDTO.setIdnum(busLineNumber);
        BusSidDTO busSidDTO = busBaseDataService.getBusSID(busLineNumberDTO);
        return busSidDTO.getSid();
    }

    /**
     * 根据站点名模糊查询
     * @param busStationLike
     * @return
     */
    public List<String> queryBusStationLike(String busStationLike) {
        return busStationDBService.queryBusStationLike(busStationLike);
    }

    /**
     * 根据线路名模糊查询
     * @param busLineLike
     * @return
     */
    public List<String> queryBusLineLike(String busLineLike) {
        return busLineDBService.queryBusLineLike(busLineLike);
    }
}

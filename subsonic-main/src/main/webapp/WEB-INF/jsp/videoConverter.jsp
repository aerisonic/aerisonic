<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>

    <style type="text/css">
        .ui-progressbar-value {
            background: #E65100;
        }
    </style>
    <script type="text/javascript" src="<c:url value="/dwr/engine.js"/>"></script>
    <script type="text/javascript" src="<c:url value="/dwr/interface/multiService.js"/>"></script>

    <script type="text/javascript" language="javascript">

        var updateInterval = 2000;

        function init() {
            $("#conversion-progressbar").progressbar({
                max: ${model.video.durationSeconds}
            });
            dwr.engine.setErrorHandler(null);
            updateConversionStatus();
        }
        function updateConversionStatus() {
            multiService.getVideoConversionStatus(${model.video.id}, conversionStatusCallback);
            setTimeout(updateConversionStatus, updateInterval);
        }
        function conversionStatusCallback(conversionStatus) {
            $("#conversion-progressbar").progressbar("option", "value", conversionStatus == null ? 0 : conversionStatus.progressSeconds);
            $("#conversion-status-new").toggle(conversionStatus != null && conversionStatus.statusNew);
            $("#conversion-status-in-progress").toggle(conversionStatus != null && conversionStatus.statusInProgress);
            $("#conversion-status-completed").toggle(conversionStatus != null && conversionStatus.statusCompleted);
            $("#conversion-status-error").toggle(conversionStatus != null && conversionStatus.statusError);

            $("#conversion-start").toggle(conversionStatus == null || conversionStatus.statusError);
            $("#conversion-cancel").toggle(conversionStatus != null && (conversionStatus.statusNew || conversionStatus.statusInProgress));

            if (conversionStatus != null && conversionStatus.statusInProgress) {
                $("#conversion-thumb").attr("src", "coverArt.view?id=${model.video.id}&auth=${model.video.hash}&size=120&offset=" + conversionStatus.progressSeconds);
                $("#conversion-status-progress").text(conversionStatus.progressString);
                updateInterval = 1000;
            } else {
                updateInterval = 2000;
            }
        }
        function startConversion() {
            multiService.startVideoConversion(${model.video.id}, conversionStatusCallback);
        }
        function cancelConversion() {
            multiService.cancelVideoConversion(${model.video.id}, conversionStatusCallback);
        }
        function back() {
            if (document.referrer) {
                location.href = document.referrer;
            } else {
                history.back();
            }
        }
    </script>
</head>

<body class="mainframe bgcolor1" style="padding-bottom:0.5em" onload="init()">

<h1><i class="fa fa-film fa-fw icon"></i>&nbsp;&nbsp;<fmt:message key="videoConverter.title"/></h1>

<p style="margin-top:1.5em">
    <fmt:message key="videoConverter.intro"/>
</p>

<div style="margin: 2em 3em;">
    <div style="float:left; width:213px; margin-right: 3em">
        <img id="conversion-thumb" src="coverArt.view?id=${model.video.id}&auth=${model.video.hash}&size=120" height="120" width="213">
        <div id="conversion-progressbar" style="width:100%; height:7px;margin-top: 0.5em"></div>
        <input id="conversion-start" style="display:none; width:100%; margin-top:1em;cursor:pointer" type="button" value="<fmt:message key="videoConverter.start"/>" onclick="startConversion()">
        <input id="conversion-cancel" style="display:none; width:100%; margin-top:1em;cursor:pointer" type="button" value="<fmt:message key="videoConverter.cancel"/>" onclick="cancelConversion()">
    </div>

    <table class="detail" style="float:left">
        <tr><td style="padding-right:1em"><b><fmt:message key="videoConverter.details.title"/></b></td><td>${model.video.title}</td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="personalsettings.format"/></b></td><td>${model.video.format}</td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="personalsettings.duration"/></b></td><td>${model.video.durationString}</td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="personalsettings.bitrate"/></b></td><td>${model.video.bitRate} Kbps</td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="personalsettings.filesize"/></b></td><td><sub:formatBytes bytes="${model.video.fileSize}"/></td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="videoConverter.details.dimension"/></b></td><td>${model.video.width} x ${model.video.height}</td></tr>
        <tr><td style="padding-right:1em"><b><fmt:message key="videoConverter.details.status"/></b></td><td>
            <span style="display:none" id="conversion-status-new"><fmt:message key="videoConverter.status.new"/></span>
            <span style="display:none" id="conversion-status-in-progress"><i class="fa fa-refresh fa-spin"></i>&nbsp;&nbsp;<fmt:message key="videoConverter.status.in_progress"/>
                (<span id="conversion-status-progress"></span>)</span>
            <span style="display:none" id="conversion-status-completed"><fmt:message key="videoConverter.status.completed"/></span>
            <span style="display:none" id="conversion-status-error"><fmt:message key="videoConverter.status.error"/></span>
        </td></tr>
    </table>
</div>

<div style="clear:both"></div>
<p style="margin-top:2em">
    <fmt:message key="videoConverter.info"/>
</p>

<p>
    <i class="fa fa-chevron-left icon"></i>&nbsp;<a href="javascript:back()"><fmt:message key="common.back"/></a>
</p>
</body>
</html>

// Generated by CoffeeScript 1.9.3
(function() {
  var formLogicExt;

  formLogicExt = function($, WebOmi) {
    var my;
    my = WebOmi.formLogic = {};
    my.send = function() {
      var request, server;
      server = WebOmi.consts.serverUrl.val();
      request = WebOmi.consts.requestCodeMirror.getValue();
      return $.ajax({
        type: "POST",
        url: server,
        data: request,
        contentType: "text/xml",
        processData: false,
        dataType: "text",
        success: function(response) {
          WebOmi.consts.responseCodeMirror.setValue(response);
          return WebOmi.consts.responseCodeMirror.autoFormatAll;
        }
      });
    };
    return WebOmi;
  };

  window.WebOmi = formLogicExt($, window.WebOmi || {});

  (function(consts, requests) {
    return consts.afterJquery(function() {
      return consts.readAllBtn.on('click', function() {
        return requests.readAll(true);
      });
    });
  })(window.WebOmi.consts, window.WebOmi.requests);

  $(function() {
    return $('.optional-parameters .panel-heading a').on('click', function() {
      var glyph;
      console.log(this);
      glyph = $(this).children('span');
      if (glyph.hasClass('glyphicon-menu-right')) {
        glyph.removeClass('glyphicon-menu-right');
        return glyph.addClass('glyphicon-menu-down');
      } else {
        glyph.removeClass('glyphicon-menu-down');
        return glyph.addClass('glyphicon-menu-right');
      }
    });
  });

}).call(this);

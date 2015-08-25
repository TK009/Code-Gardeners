// Generated by CoffeeScript 1.9.3
(function() {
  var requestsExt,
    hasProp = {}.hasOwnProperty;

  requestsExt = function(WebOmi) {
    var addValueToAll, addValueWhenWrite, currentParams, maybeInsertBefore, my, removeValueFromAll, updateSetterForAttr;
    my = WebOmi.requests = {};
    my.xmls = {
      readAll: "<?xml version=\"1.0\"?>\n<omi:omiEnvelope xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\"\n    version=\"1.0\" ttl=\"0\">\n  <omi:read msgformat=\"odf\">\n    <omi:msg>\n      <Objects xmlns=\"odf.xsd\"></Objects>\n    </omi:msg>\n  </omi:read>\n</omi:omiEnvelope>",
      template: "<?xml version=\"1.0\"?>\n<omi:omiEnvelope xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\"\n    version=\"1.0\" ttl=\"0\">\n  <omi:read msgformat=\"odf\">\n    <omi:msg>\n    </omi:msg>\n  </omi:read>\n</omi:omiEnvelope>\n"
    };
    my.defaults = {};
    my.defaults.empty = function() {
      return {
        name: "empty",
        request: null,
        ttl: 0,
        callback: null,
        requestID: null,
        odf: null,
        interval: null,
        newest: null,
        oldest: null,
        begin: null,
        end: null,
        requestDoc: null,
        msg: true
      };
    };
    my.defaults.readAll = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "readAll",
        request: "read",
        odf: ["Objects"]
      });
    };
    my.defaults.read = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "readOnce",
        request: "read",
        odf: ["Objects"]
      });
    };
    my.defaults.subscription = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "subscription",
        request: "read",
        interval: 5,
        ttl: 60,
        odf: ["Objects"]
      });
    };
    my.defaults.poll = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "poll",
        request: "read",
        requestID: 1,
        msg: false
      });
    };
    my.defaults.write = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "write",
        request: "write",
        odf: ["Objects"]
      });
    };
    my.defaults.cancel = function() {
      return $.extend({}, my.defaults.empty(), {
        name: "cancel",
        request: "cancel",
        requestID: 1,
        odf: null,
        msg: false
      });
    };
    currentParams = my.defaults.empty();
    my.getCurrentParams = function() {
      return $.extend({}, currentParams);
    };
    my.confirmOverwrite = function(oldVal, newVal) {
      return confirm("You have edited the request manually.\n Do you want to overwrite " + oldVal.toString + " with " + newVal.toString);
    };
    addValueWhenWrite = function(odfInfoItem, values) {
      var doc, i, len, results, val, value;
      if (values == null) {
        values = [
          {
            value: "0"
          }
        ];
      }
      if (currentParams.request === 'write') {
        doc = odfInfoItem.ownerDocument;
        results = [];
        for (i = 0, len = values.length; i < len; i++) {
          value = values[i];
          val = WebOmi.omi.createOdfValue(doc, value.value, value.valuetype, value.valuetime);
          results.push(odfInfoItem.appendChild(val));
        }
        return results;
      }
    };
    addValueToAll = function(doc) {
      var i, info, infos, len, results;
      infos = WebOmi.omi.evaluateXPath(doc, "//odf:InfoItem");
      results = [];
      for (i = 0, len = infos.length; i < len; i++) {
        info = infos[i];
        results.push(addValueWhenWrite(info));
      }
      return results;
    };
    removeValueFromAll = function(doc) {
      var i, len, results, val, vals;
      vals = WebOmi.omi.evaluateXPath(doc, "//odf:value");
      results = [];
      for (i = 0, len = vals.length; i < len; i++) {
        val = vals[i];
        results.push(val.parentNode.removeChild(val));
      }
      return results;
    };
    my.removePathFromOdf = function(odfTreeNode, odfObjects) {
      var allOdfElems, elem, i, id, lastOdfElem, len, maybeChild, node, nodeElems, o;
      o = WebOmi.omi;
      nodeElems = $.makeArray(odfTreeNode.parentsUntil("#Objects", "li"));
      nodeElems.reverse();
      nodeElems.push(odfTreeNode);
      lastOdfElem = odfObjects;
      allOdfElems = (function() {
        var i, len, results;
        results = [];
        for (i = 0, len = nodeElems.length; i < len; i++) {
          node = nodeElems[i];
          id = $(node).children("a").text();
          maybeChild = o.getOdfChild(id, lastOdfElem);
          if (maybeChild != null) {
            lastOdfElem = maybeChild;
          }
          results.push(maybeChild);
        }
        return results;
      })();
      lastOdfElem.parentNode.removeChild(lastOdfElem);
      allOdfElems.pop();
      allOdfElems.reverse();
      for (i = 0, len = allOdfElems.length; i < len; i++) {
        elem = allOdfElems[i];
        if ((elem != null) && !o.hasOdfChildren(elem)) {
          elem.parentNode.removeChild(elem);
        }
      }
      return odfObjects;
    };
    maybeInsertBefore = function(parent, beforeTarget, insertElem) {
      if (beforeTarget != null) {
        return parent.insertBefore(insertElem, beforeTarget);
      } else {
        return parent.appendChild(insertElem);
      }
    };
    my.addPathToOdf = function(odfTreeNode, odfObjects) {
      var currentOdfNode, i, id, info, len, maybeChild, maybeDesc, maybeValues, meta, metadata, metainfo, metas, node, nodeElems, o, object, odfDoc, odfElem, siblingObject, siblingValue;
      o = WebOmi.omi;
      odfDoc = odfObjects.ownerDocument || odfObjects;
      if ((odfTreeNode[0] == null) || odfTreeNode[0].id === "Objects") {
        return odfObjects;
      }
      nodeElems = $.makeArray(odfTreeNode.parentsUntil("#Objects", "li"));
      nodeElems.reverse();
      nodeElems.push(odfTreeNode);
      currentOdfNode = odfObjects;
      for (i = 0, len = nodeElems.length; i < len; i++) {
        node = nodeElems[i];
        id = $(node).children("a").text();
        maybeChild = o.getOdfChild(id, currentOdfNode);
        if (maybeChild != null) {
          currentOdfNode = maybeChild;
        } else {
          odfElem = (function() {
            var j, len1;
            switch (WebOmi.consts.odfTree.get_type(node)) {
              case "object":
                object = o.createOdfObject(odfDoc, id);
                return currentOdfNode.appendChild(object);
              case "metadata":
                meta = o.createOdfMetaData(odfDoc);
                metas = $(node).data("metadatas");
                if ((metas != null) && currentParams.request === "write") {
                  for (j = 0, len1 = metas.length; j < len1; j++) {
                    metadata = metas[j];
                    metainfo = o.createOdfInfoItem(odfDoc, metadata.name, [
                      {
                        value: metadata.value,
                        vAluetype: metadata.type
                      }
                    ], metadata.description);
                    meta.appendChild(metainfo);
                  }
                }
                siblingValue = o.evaluateXPath(currentOdfNode, "odf:value[1]")[0];
                return maybeInsertBefore(currentOdfNode, siblingValue, meta);
              case "infoitem":
                info = currentParams.request === "write" ? (maybeValues = $(node).data("values"), maybeDesc = $(node).data("description"), o.createOdfInfoItem(odfDoc, id, maybeValues, maybeDesc)) : o.createOdfInfoItem(odfDoc, id);
                siblingObject = o.evaluateXPath(currentOdfNode, "odf:Object[1]")[0];
                return maybeInsertBefore(currentOdfNode, siblingObject, info);
            }
          })();
          currentOdfNode = odfElem;
        }
      }
      return odfObjects;
    };
    updateSetterForAttr = function(name, attrParentXPath) {
      return {
        update: function(newVal) {
          var attrParents, doc, i, len, o, parent;
          o = WebOmi.omi;
          doc = currentParams.requestDoc;
          if (currentParams[name] !== newVal) {
            attrParents = o.evaluateXPath(doc, attrParentXPath);
            if (attrParents == null) {
              WebOmi.error("Tried to update " + name + ", but " + attrParentXPath + " was not found in", doc);
            } else {
              for (i = 0, len = attrParents.length; i < len; i++) {
                parent = attrParents[i];
                if (newVal != null) {
                  parent.setAttribute(name, newVal);
                } else {
                  parent.removeAttribute(name);
                }
              }
            }
            currentParams[name] = newVal;
            return newVal;
          }
        }
      };
    };
    my.params = {
      name: {
        update: function(name) {
          var requestTag;
          if (currentParams.name !== name) {
            currentParams.name = name;
            requestTag = (function() {
              switch (name) {
                case "poll":
                case "subscription":
                case "readAll":
                case "readReq":
                case "readOnce":
                case "template":
                  return "read";
                case "empty":
                  return null;
                default:
                  return name;
              }
            })();
            return my.params.request.update(requestTag);
          }
        }
      },
      request: {
        update: function(reqName) {
          var attr, child, currentReq, doc, i, len, newReq, oldReqName, ref;
          oldReqName = currentParams.request;
          if (currentParams.requestDoc == null) {
            return my.forceLoadParams(my.defaults[reqName]());
          } else if (reqName !== oldReqName) {
            doc = currentParams.requestDoc;
            currentReq = WebOmi.omi.evaluateXPath(doc, "omi:omiEnvelope/*")[0];
            newReq = WebOmi.omi.createOmi(reqName, doc);
            ref = currentReq.attributes;
            for (i = 0, len = ref.length; i < len; i++) {
              attr = ref[i];
              newReq.setAttribute(attr.name, attr.value);
            }
            while (child = currentReq.firstChild) {
              newReq.appendChild(child);
              if (child === currentReq.firstChild) {
                currentReq.removeChild(child);
              }
            }
            currentReq.parentNode.replaceChild(newReq, currentReq);
            currentParams.request = reqName;
            if (reqName === "write") {
              my.params.odf.update(currentParams.odf);
            } else if (oldReqName === "write") {
              my.params.odf.update(currentParams.odf);
            }
            return reqName;
          }
        }
      },
      ttl: updateSetterForAttr("ttl", "omi:omiEnvelope"),
      callback: updateSetterForAttr("callback", "omi:omiEnvelope/*"),
      requestID: {
        update: function(newVal) {
          var doc, existingIDs, i, id, idTxt, j, k, len, len1, len2, newId, o, parent, parentXPath, parents;
          o = WebOmi.omi;
          doc = currentParams.requestDoc;
          parentXPath = "omi:omiEnvelope/*";
          if (currentParams.requestID !== newVal) {
            parents = o.evaluateXPath(doc, parentXPath);
            if (parents == null) {
              WebOmi.error("Tried to update requestID, but " + parentXPath + " not found in", doc);
            } else {
              existingIDs = o.evaluateXPath(doc, "//omi:requestID");
              if (newVal != null) {
                if (existingIDs.some(function(elem) {
                  return elem.textContent.trim() === newVal.toString();
                })) {
                  return;
                } else {
                  for (i = 0, len = parents.length; i < len; i++) {
                    parent = parents[i];
                    for (j = 0, len1 = existingIDs.length; j < len1; j++) {
                      id = existingIDs[j];
                      id.parentNode.removeChild(id);
                    }
                    newId = o.createOmi("requestID", doc);
                    idTxt = doc.createTextNode(newVal.toString());
                    newId.appendChild(idTxt);
                    parent.appendChild(newId);
                  }
                }
              } else {
                for (k = 0, len2 = existingIDs.length; k < len2; k++) {
                  id = existingIDs[k];
                  id.parentNode.removeChild(id);
                }
              }
            }
            currentParams.requestID = newVal;
          }
          return newVal;
        }
      },
      odf: {
        update: function(paths) {
          var doc, i, j, len, len1, msg, o, obs, obss, odfTreeNode, path;
          o = WebOmi.omi;
          doc = currentParams.requestDoc;
          if ((paths != null) && paths.length > 0) {
            obs = o.createOdfObjects(doc);
            for (i = 0, len = paths.length; i < len; i++) {
              path = paths[i];
              odfTreeNode = $(jqesc(path));
              my.addPathToOdf(odfTreeNode, obs);
            }
            if (currentParams.msg) {
              msg = o.evaluateXPath(currentParams.requestDoc, "//omi:msg")[0];
              if (msg == null) {
                my.params.msg.update(currentParams.msg);
                return;
              }
              while (msg.firstChild) {
                msg.removeChild(msg.firstChild);
              }
              msg.appendChild(obs);
            }
          } else {
            obss = WebOmi.omi.evaluateXPath(doc, "//odf:Objects");
            for (j = 0, len1 = obss.length; j < len1; j++) {
              obs = obss[j];
              obs.parentNode.removeChild(obs);
            }
          }
          currentParams.odf = paths;
          return paths;
        },
        add: function(path) {
          var currentObjectsHead, fl, msg, o, objects, odfTreeNode, req;
          o = WebOmi.omi;
          fl = WebOmi.formLogic;
          odfTreeNode = $(jqesc(path));
          req = currentParams.requestDoc;
          if (req != null) {
            currentObjectsHead = o.evaluateXPath(req, '//odf:Objects')[0];
            if (currentObjectsHead != null) {
              my.addPathToOdf(odfTreeNode, currentObjectsHead);
            } else if (currentParams.msg) {
              objects = o.createOdfObjects(req);
              my.addPathToOdf(odfTreeNode, objects);
              msg = o.evaluateXPath(req, "//omi:msg")[0];
              if (msg != null) {
                msg.appendChild(objects);
              } else {
                WebOmi.error("error, msg not found: " + msg);
              }
            }
          }
          if (currentParams.odf != null) {
            currentParams.odf.push(path);
          } else {
            currentParams.odf = [path];
          }
          return path;
        },
        remove: function(path) {
          var fl, o, odfObjects, odfTreeNode, req;
          o = WebOmi.omi;
          fl = WebOmi.formLogic;
          req = currentParams.requestDoc;
          if (currentParams.msg && (req != null)) {
            odfTreeNode = $(jqesc(path));
            odfObjects = o.evaluateXPath(req, '//odf:Objects')[0];
            if (odfObjects != null) {
              my.removePathFromOdf(odfTreeNode, odfObjects);
            }
          }
          if (currentParams.odf != null) {
            currentParams.odf = currentParams.odf.filter(function(p) {
              return p !== path;
            });
          } else {
            currentParams.odf = [];
          }
          return path;
        }
      },
      interval: updateSetterForAttr("interval", "omi:omiEnvelope/*"),
      newest: updateSetterForAttr("newest", "omi:omiEnvelope/*"),
      oldest: updateSetterForAttr("oldest", "omi:omiEnvelope/*"),
      begin: updateSetterForAttr("begin", "omi:omiEnvelope/*"),
      end: updateSetterForAttr("end", "omi:omiEnvelope/*"),
      msg: {
        update: function(hasMsg) {
          var doc, i, len, m, msg, o, requestElem;
          o = WebOmi.omi;
          doc = currentParams.requestDoc;
          if (hasMsg === currentParams.msg) {
            return;
          }
          if (hasMsg) {
            msg = o.createOmi("msg", doc);
            requestElem = o.evaluateXPath(doc, "/omi:omiEnvelope/*")[0];
            if (requestElem != null) {
              requestElem.appendChild(msg);
              requestElem.setAttribute("msgformat", "odf");
              currentParams.msg = hasMsg;
              my.params.odf.update(currentParams.odf);
            } else {
              WebOmi.error("ERROR: No request found");
              return;
            }
          } else {
            msg = o.evaluateXPath(doc, "/omi:omiEnvelope/*/omi:msg");
            for (i = 0, len = msg.length; i < len; i++) {
              m = msg[i];
              m.parentNode.removeChild(m);
            }
            requestElem = o.evaluateXPath(doc, "/omi:omiEnvelope/*")[0];
            if (requestElem != null) {
              requestElem.removeAttribute("msgformat");
            }
          }
          currentParams.msg = hasMsg;
          return hasMsg;
        }
      }
    };
    my.generate = function() {
      return WebOmi.formLogic.setRequest(currentParams.requestDoc);
    };
    my.forceLoadParams = function(omiRequestObject, useOldDoc) {
      var cp, key, newParams, newVal, o, ref, thing, uiWidget;
      if (useOldDoc == null) {
        useOldDoc = false;
      }
      o = WebOmi.omi;
      cp = currentParams;
      for (key in omiRequestObject) {
        if (!hasProp.call(omiRequestObject, key)) continue;
        newVal = omiRequestObject[key];
        uiWidget = WebOmi.consts.ui[key];
        if (uiWidget != null) {
          uiWidget.set(newVal);
        }
      }
      if (!useOldDoc || (cp.requestDoc == null)) {
        cp.requestDoc = o.parseXml(my.xmls.template);
      }
      newParams = $.extend({}, cp, omiRequestObject);
      if ((newParams.request != null) && newParams.request.length > 0 && (newParams.ttl != null)) {
        ref = my.params;
        for (key in ref) {
          if (!hasProp.call(ref, key)) continue;
          thing = ref[key];
          thing.update(newParams[key]);
          WebOmi.debug("updated " + key + ":", currentParams[key]);
        }
        my.generate();
      } else if (newParams.name === "empty") {
        currentParams = omiRequestObject;
        my.generate();
      } else {
        WebOmi.error("tried to generate request, but missing a required parameter (name, ttl)", newParams);
      }
      return null;
    };
    my.readAll = function(fastForward) {
      my.forceLoadParams(my.defaults.readAll());
      if (fastForward) {
        return WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr);
      }
    };
    return WebOmi;
  };

  window.WebOmi = requestsExt(window.WebOmi || {});

}).call(this);

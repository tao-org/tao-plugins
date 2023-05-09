package org.w3id.cwl.cwl1_2.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class SaveableImpl implements Saveable {
  public SaveableImpl(){}
  public SaveableImpl(Object doc, String baseUri, LoadingOptions loadingOptions, String docRoot) {}

  public Object save(Object val, boolean top, String baseURL) {
    return save(val, top, baseURL, true);
  }

  public Object save(Object val, boolean top, String baseUrl, boolean relativeUris) {
    if (val == null) {
      return null;
    }

    if (val instanceof Enum<?>) {
      val = val.toString();
    }

    if (val instanceof Saveable) {
      return ((Saveable) val).save(top, baseUrl, relativeUris);
    }

    if (val instanceof List<?>) {
      List<Object> r = new ArrayList<>();
      for (Object v : (ArrayList) val) {
        r.add(save(v, false, baseUrl, relativeUris));
      }
      return r;
    }

    return val;
  }

  public Object saveRelativeUri(Object uri, boolean scopedId, boolean relativeUris, Integer refScope, String baseUrl)
  {
    if (baseUrl == null) {
      baseUrl = "";
    }

    if (uri == null) {
      return null;
    }

    if (uri instanceof Enum<?>) {
      uri = uri.toString();
    }

    if (relativeUris == false || (uri instanceof String && uri.equals(baseUrl))) {
      return uri;
    }

    if (uri instanceof List)
    {
      List<Object> r = new ArrayList<>();
      for (Object v : (ArrayList) uri) {
        r.add(saveRelativeUri(v, scopedId, relativeUris, refScope, baseUrl));
      }
      return r;
    } else if (uri instanceof String) {
      try {
        URI uriSplit = new URI(uri.toString());
        URI baseSplit = new URI(baseUrl);
        if ((uriSplit.isAbsolute() && uriSplit.getPath().length() < 1) && (baseSplit.isAbsolute() && baseSplit.getPath().length() < 1)) {
          throw new ValidationException("Uri or baseurl need to contain a path");
        }
        if (uriSplit.isAbsolute() && baseSplit.isAbsolute() && uriSplit.getScheme().equals(baseSplit.getScheme())
                && uriSplit.getHost().equals(baseSplit.getHost())) {
          if (!uriSplit.getPath().equals(baseSplit.getPath())) {
            String p = baseSplit.relativize(uriSplit).toString();
            if (uriSplit.getFragment().length() > 0) {
              p = p + "#" + uriSplit.getFragment();
            }
            return p;
          }
          String baseFrag = baseSplit.getFragment() + "/";
          if (refScope != null) {
            List<String> sp = Arrays.stream(baseFrag.split("/")).collect(Collectors.toList());
            int i = 0;
            while (i < refScope) {
              sp.remove(sp.size() - 1);
              i += 1;
            }
            baseFrag = String.join("/", sp);
          }
          if (uriSplit.getFragment().startsWith(baseFrag)) {
            return uriSplit.getFragment().substring(baseFrag.length());
          } else {
            return uriSplit.getFragment();
          }
        } else {
          return save(uri, false, baseUrl);
        }
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    throw new ValidationException("uri needs to be of type List or String");
  }
}

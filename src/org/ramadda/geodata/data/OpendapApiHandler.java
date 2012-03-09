/*
* Copyright 2008-2011 Jeff McWhirter/ramadda.org
*
* Permission is hereby granted, free of charge, to any person obtaining a copy of this 
* software and associated documentation files (the "Software"), to deal in the Software 
* without restriction, including without limitation the rights to use, copy, modify, 
* merge, publish, distribute, sublicense, and/or sell copies of the Software, and to 
* permit persons to whom the Software is furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in all copies 
* or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
* INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
* PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
* FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR 
* OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
* DEALINGS IN THE SOFTWARE.
*/

package org.ramadda.geodata.data;


import org.ramadda.repository.*;

import org.w3c.dom.*;

import ucar.unidata.util.IOUtil;


import java.util.Hashtable;


/**
 * Provides a top-level API /repository/opendap/<entry path>/entry.das
 *
 */
public class OpendapApiHandler extends RepositoryManager implements RequestHandler {

    /** My id. defined in resources/opendapapi.xml */
    public static final String API_ID = "opendap";

    /** Top-level path element */
    public static final String PATH_OPENDAP = "opendap";

    /** opendap suffix to use */
    public static final String OPENDAP_SUFFIX = "entry.das";

    /** the output handler to pass opendap calls to */
    private DataOutputHandler dataOutputHandler;

    /**
     * ctor
     *
     * @param repository the repository
     * @param node xml from api.xml
     * @param props propertiesn
     *
     * @throws Exception on badness
     */
    public OpendapApiHandler(Repository repository, Element node,
                             Hashtable props)
            throws Exception {
        super(repository);
    }

    /**
     * makes the opendap url for the entry
     *
     * @param entry the entry
     *
     * @return opendap url
     */
    public String getOpendapUrl(Entry entry) {
        String url = getRepository().getUrlBase() + "/" + PATH_OPENDAP + "/"
                     + entry.getFullName() + "/" + OPENDAP_SUFFIX;
        url = url.replaceAll(" ", "+");
        return url;
    }


    /**
     * handle the request
     *
     * @param request request
     *
     * @return result
     *
     * @throws Exception on badness
     */
    public Result processOpendapRequest(Request request) throws Exception {
        if (dataOutputHandler == null) {
            dataOutputHandler =
                (DataOutputHandler) getRepository().getOutputHandler(
                    DataOutputHandler.OUTPUT_OPENDAP);
        }
        //Find the entry path
        String prefix = getRepository().getUrlBase() + "/" + PATH_OPENDAP;
        String path   = request.getRequestPath();
        path = path.substring(prefix.length());
        path = IOUtil.getFileRoot(path);
        path = path.replaceAll("\\+", " ");

        //Find the entry
        Entry entry = getEntryManager().findEntryFromName(request, path,
                          request.getUser(), false);
        if (entry == null) {
            throw new IllegalArgumentException("Could not find entry:"
                    + path);
        }
        return dataOutputHandler.outputOpendap(request, entry);
    }



}
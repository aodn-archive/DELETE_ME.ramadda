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

package org.ramadda.repository.metadata;


import com.drew.imaging.jpeg.*;
import com.drew.lang.*;

import com.drew.metadata.*;
import com.drew.metadata.exif.*;
import com.drew.metadata.iptc.IptcDirectory;


import org.ramadda.repository.*;

import ucar.unidata.ui.ImageUtils;

import ucar.unidata.util.IOUtil;

import java.awt.Image;
import java.awt.image.*;

import java.io.File;

import java.util.Date;
import java.util.Hashtable;
import java.util.List;


/**
 * A class for handling JPEG Metadata
 *
 * @author RAMADDA Development Team
 */
public class JpegMetadataHandler extends MetadataHandler {

    /** Camera Direction type */
    public static final String TYPE_CAMERA_DIRECTION = "camera.direction";


    /**
     * Construct a new instance for the repository
     *
     * @param repository  the repository
     *
     * @throws Exception  problems
     */
    public JpegMetadataHandler(Repository repository) throws Exception {
        super(repository);
    }


    /**
     * Get the initial metadata
     *
     * @param request  the request
     * @param entry  the entry
     * @param metadataList the metadata list
     * @param extra  extra stuff
     * @param shortForm  true for shortform
     */
    public void getInitialMetadata(Request request, Entry entry,
                                   List<Metadata> metadataList,
                                   Hashtable extra, boolean shortForm) {

        String path = entry.getResource().getPath();

        if ( !entry.getResource().isImage()) {
            return;
        }

        try {
            Image image = ImageUtils.readImage(entry.getResource().getPath(),
                              false);

            ImageUtils.waitOnImage(image);
            Image newImage = ImageUtils.resize(image, 100, -1);

            ImageUtils.waitOnImage(newImage);
            File f = getStorageManager().getTmpFile(request,
                         IOUtil.stripExtension(entry.getName())
                         + "_thumb.jpg");
            ImageUtils.writeImageToFile(newImage, f);


            String fileName = getStorageManager().copyToEntryDir(entry,
                                  f).getName();
            Metadata thumbnailMetadata =
                new Metadata(getRepository().getGUID(), entry.getId(),
                             ContentMetadataHandler.TYPE_THUMBNAIL, false,
                             fileName, null, null, null, null);

            metadataList.add(thumbnailMetadata);

        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }


        if ( !(path.toLowerCase().endsWith(".jpg")
                || path.toLowerCase().endsWith(".jpeg"))) {
            return;
        }
        try {
            File jpegFile = new File(path);
            com.drew.metadata.Metadata metadata =
                JpegMetadataReader.readMetadata(jpegFile);
            com.drew.metadata.Directory exifDir =
                metadata.getDirectory(ExifDirectory.class);
            com.drew.metadata.Directory dir =
                metadata.getDirectory(GpsDirectory.class);
            com.drew.metadata.Directory iptcDir =
                metadata.getDirectory(IptcDirectory.class);

            if (exifDir != null) {
                //This tells ramadda that something was added
                if (exifDir.containsTag(
                        ExifDirectory.TAG_DATETIME_ORIGINAL)) {
                    Date dttm =
                        exifDir.getDate(ExifDirectory.TAG_DATETIME_ORIGINAL);
                    if (dttm != null) {
                        entry.setStartDate(dttm.getTime());
                        entry.setEndDate(dttm.getTime());
                        extra.put("1", "");
                    }
                }

            }

            if (iptcDir != null) {
                // Get caption and make it the description if the user didn't add one
                if (iptcDir.containsTag(IptcDirectory.TAG_CAPTION)) {
                    String caption =
                        iptcDir.getString(IptcDirectory.TAG_CAPTION);
                    if ((caption != null)
                            && entry.getDescription().isEmpty()) {
                        entry.setDescription(caption);
                        //This tells ramadda that something was added
                        extra.put("1", "");
                    }
                }

            }

            if (dir.containsTag(GpsDirectory.TAG_GPS_IMG_DIRECTION)) {
                Metadata dirMetadata =
                    new Metadata(getRepository().getGUID(), entry.getId(),
                                 TYPE_CAMERA_DIRECTION, DFLT_INHERITED,
                                 "" + getValue(dir,
                                     GpsDirectory
                                         .TAG_GPS_IMG_DIRECTION), Metadata
                                             .DFLT_ATTR, Metadata.DFLT_ATTR,
                                                 Metadata.DFLT_ATTR,
                                                 Metadata.DFLT_EXTRA);

                metadataList.add(dirMetadata);
            }

            if ( !dir.containsTag(GpsDirectory.TAG_GPS_LATITUDE)) {
                return;
            }
            double latitude  = getValue(dir, GpsDirectory.TAG_GPS_LATITUDE);
            double longitude = getValue(dir, GpsDirectory.TAG_GPS_LONGITUDE);
            String lonRef = dir.getString(GpsDirectory.TAG_GPS_LONGITUDE_REF);
            String latRef = dir.getString(GpsDirectory.TAG_GPS_LATITUDE_REF);
            if ((lonRef != null) && lonRef.equalsIgnoreCase("W")) {
                longitude = -longitude;
            }
            if ((latRef != null) && latRef.equalsIgnoreCase("S")) {
                latitude = -latitude;
            }
            double altitude = (dir.containsTag(GpsDirectory.TAG_GPS_ALTITUDE)
                               ? getValue(dir, GpsDirectory.TAG_GPS_ALTITUDE)
                               : 0);
            try {
                int altRef = dir.getInt(GpsDirectory.TAG_GPS_ALTITUDE_REF);
                if (altRef > 0) {
                    altitude = -altitude;
                }
            } catch (MetadataException mde) {
                // means that the tag didn't exist
                // with version 2.5.0 of metadata extractor could move to 
                // getInteger which will return null instead of throw exception
            }
            entry.setLocation(latitude, longitude, altitude);
            //This tells ramadda that something was added
            extra.put("1", "");
        } catch (Exception exc) {
            getRepository().getLogManager().logError("Processing jpg:"
                    + path, exc);
        }

    }


    /**
     * Get the value of a tag as a double
     *
     * @param dir  the directory
     * @param tag  the tag
     *
     * @return  the double value
     *
     * @throws Exception  couldn't create the double
     */
    private double getValue(Directory dir, int tag) throws Exception {
        try {
            Rational[] comps = dir.getRationalArray(tag);
            if (comps.length == 3) {
                int   deg = comps[0].intValue();
                float min = comps[1].floatValue();
                float sec = comps[2].floatValue();
                sec += (min % 1) * 60;
                return deg + min / 60 + sec / 60 / 60;
            }
        } catch (Exception exc) {
            //Ignore this
        }
        return dir.getDouble(tag);
    }


}

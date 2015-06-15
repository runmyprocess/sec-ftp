package org.runmyprocess.sec;


import java.io.*;
import java.util.logging.Level;
import java.net.SocketException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPFile;
import org.runmyprocess.json.JSONArray;
import org.runmyprocess.json.JSONObject;

/**
 *
 * @author Malcolm Haslam <mhaslam@runmyprocess.com>
 *
 * Copyright (C) 2014 Fujitsu RunMyProcess
 *
 * This file is part of RunMyProcess SEC.
 *
 * RunMyProcess SEC is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License Version 2.0 (the "License");
 *
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */



public class FTP implements ProtocolInterface {

    // Logging instance
    private static final  SECLogManager LOG = new SECLogManager(FTP.class.getName());

    private static  enum Task {
        PING, GET,PUT, LIST, DELETE, MKDIR, RMDIR, RENAME, DEFAULT
    }

    private FTPClient client;
    private Response response = new Response();
    private boolean logged = false;


	public FTP() {
		// TODO Auto-generated constructor stub
        this.client = new FTPClient();
	}

    private String encodeInputStreamToBase64Binary(InputStream is)
            throws IOException {

        byte[] bytes =  IOUtils.toByteArray(is);
        byte[] encoded = Base64.encodeBase64(bytes);
        String encodedString = new String(encoded);

        return encodedString;
    }

    private boolean connect (String host, String login, String password, int port ) throws IOException{
        client.setConnectTimeout(15000);
        //this.client.enterLocalPassiveMode() ;
        try{
            if(!client.isConnected())  {
                LOG.log("Connecting...",Level.INFO);
                client.connect(host,port);
            }
        }catch(Exception e){
            throw new IOException(e.toString());
        }

        int reply = client.getReplyCode();
        LOG.log("connected:"+client.getReplyString()+" replyCode:"+reply+" / "+FTPReply.isPositiveCompletion(reply),Level.INFO);
        if(FTPReply.isPositiveCompletion(reply)) {

            this.client.enterLocalPassiveMode() ;
            logged =client.login(login, password);
            LOG.log("Logged in :"+logged,Level.INFO)  ;
            return logged;
        }else{
            throw new IOException("Error connecting to the FTP server");
        }

    }

    /**
     * Error manager
     * @param error  error message
     * @return error jsonObject
     */
	private JSONObject FTPError(String error){
		response.setStatus(500);//sets the return status to internal server error
		JSONObject errorObject = new JSONObject();
		errorObject.put("error", error);
		return errorObject;
	}

    private void ping(JSONObject jsonObject)throws Exception{

    }

    private void fetchFile(JSONObject jsonObject)throws Exception{
        LOG.log("fetching file..." ,Level.INFO);
        InputStream is = this.client.retrieveFileStream(jsonObject.getString("file"));
        if(is == null || is.available()==0){
          //is.close();
          throw new Exception("the file was not found or could not be read");
        }
        String FileBase64 = this.encodeInputStreamToBase64Binary(is);
        is.close();
        LOG.log("File fetched successfully !",Level.INFO);
        response.setStatus(200);//sets the return status to 200
        JSONObject resp = new JSONObject();
        resp.put("file", jsonObject.getString("file"));//sends the file fetched
        resp.put("data",FileBase64);
        response.setData(resp);

    }

    private void upload(JSONObject jsonObject)throws Exception{
        //FileInputStream inputStream = new FileInputStream("files/fileToUpload.txt");
        LOG.log("uploading files...",Level.INFO);
        for (Object fileObject : jsonObject.getJSONArray("files")){
            JSONObject file = (JSONObject)fileObject;
            byte[] decoded = Base64.decodeBase64(file.getString("content"));
            ByteArrayInputStream bis = new ByteArrayInputStream(decoded);

            boolean uploaded = this.client.storeFile(jsonObject.getString("path") + file.getString("name"), bis);
            if (uploaded) {
                LOG.log("File uploaded successfully !",Level.INFO);
                response.setStatus(200);//sets the return status to 200
                JSONObject resp = new JSONObject();
                resp.put("message", "File uploaded successfully !");//sends the success message
                response.setData(resp);
            } else {
                throw new Exception("Error in uploading file");
            }
        }
    }

    private void remove(JSONObject jsonObject)throws Exception{
        boolean allSuccessFlag=true;
        LOG.log("Removing files...",Level.INFO);
        for (Object fileObject : jsonObject.getJSONArray("files")){
            String file = (String)fileObject;
            boolean deleted = this.client.deleteFile(file);
            if (deleted) {
                LOG.log("File deleted!",Level.INFO);
                allSuccessFlag=true;
            } else{
                LOG.log("Error deleting!",Level.INFO);
                allSuccessFlag=false;
            }

            if (allSuccessFlag) {
                LOG.log("All files where deleted successfully",Level.INFO);
                response.setStatus(200);//sets the return status to 200
                JSONObject resp = new JSONObject();
                resp.put("message", "All files where deleted successfully");//sends the success message
                response.setData(resp);
            } else {
                LOG.log("Some Files where not deleted!",Level.INFO);
                response.setStatus(200);//sets the return status to 200
                JSONObject resp = new JSONObject();
                resp.put("message", "Some or all files could not be deleted");//sends the success message
                response.setData(resp);
            }
        }
    }


    private void rename(JSONObject jsonObject)throws Exception{
        boolean allSuccessFlag=true;
        LOG.log("Renaming",Level.INFO);
        boolean renamed = client.rename(jsonObject.getString("original"),jsonObject.getString("new"));
        if (renamed) {
            LOG.log("File renamed",Level.INFO);
            response.setStatus(200);//sets the return status to 200
            JSONObject resp = new JSONObject();
            resp.put("message", "File renamed successfully");//sends the success message
            response.setData(resp);
        } else {
            throw new Exception("Error renaming file");
        }

    }

    private void createDir(JSONObject jsonObject)throws Exception{
        boolean created = client.makeDirectory(jsonObject.getString("path"));

        if (created) {
            LOG.log("Directory Created",Level.INFO);
            response.setStatus(200);//sets the return status to 200
            JSONObject resp = new JSONObject();
            resp.put("message", "Directory created successfully");//sends the success message
            response.setData(resp);
        } else {
            throw new Exception("Error creating directory");
        }

    }
    private void removeDir(JSONObject jsonObject)throws Exception{

        boolean created = client.removeDirectory(jsonObject.getString("path"));

        if (created) {
            LOG.log("Directory removed",Level.INFO);
            response.setStatus(200);//sets the return status to 200
            JSONObject resp = new JSONObject();
            resp.put("message", "Directory removed successfully");//sends the success message
            response.setData(resp);
        } else {
            throw new Exception("Error removing directory!");
        }

    }

    private void listFiles(JSONObject jsonObject)throws Exception{
        // get all files from server and store them in an array of
        // FTPFiles
        JSONArray fileInfo = new JSONArray();
        LOG.log("LIST directories: "+jsonObject.getString("path"),Level.INFO);
        String[] directories = this.client.listNames(jsonObject.getString("path"));
        LOG.log("LIST files: "+jsonObject.getString("path"),Level.INFO);
        FTPFile[] files = this.client.listFiles(jsonObject.getString("path"));
        LOG.log("LIST DONE",Level.INFO);

        for (FTPFile file : files) {
            if (file.getType() == FTPFile.FILE_TYPE) {
                JSONObject fileObject = new JSONObject();
                fileObject.put("Name",file.getName());
                fileObject.put("Size",FileUtils.byteCountToDisplaySize(file.getSize()));
                fileObject.put("Timestamp",file.getTimestamp().getTime().toString());
                fileInfo.add(fileObject);
            }
        }
        response.setStatus(200);//sets the return status to 200
        JSONObject resp = new JSONObject();
        resp.put("files", fileInfo);//sends the info inside an object
        resp.put("directories", directories);
        response.setData(resp);
    }

    /**
     * receives the object with the path to look for the file and read the configuration file
     * @param jsonObject
     * @param configPath
     */
	  @Override
	public void accept(JSONObject jsonObject,String configPath) {
        try {
            LOG.log("NEW REQUEST",Level.INFO);
            Config config = new Config("configFiles"+File.separator+"FTP.config",true);//finds and reads the config file
            // get an ftpClient object
            if (client==null||jsonObject.containsKey("FTPType")){
                if(jsonObject.getString("FTPType")=="FTP"){
                    this.client = new FTPClient();
                }else if (jsonObject.getString("FTPType")=="FTPS") {
                    this.client=new FTPSClient();
                }
            }
            LOG.log("NEW REQUEST CLIENT SET",Level.INFO);
                try {
                    // pass directory path on server to connect
                    if(!this.connect(config.getProperty("host"), jsonObject.getString("user"),
                           jsonObject.getString("password"), Integer.parseInt(config.getProperty("port")))){
                       throw new Exception("Unable to loggin to FTP");
                   }
                    LOG.log("Connection established...",Level.INFO);

                    Task task =  Task.DEFAULT;
                    if(jsonObject.containsKey("task"))
                        try {
                            task = Task.valueOf(jsonObject.getString("task"));
                        }  catch (Exception ex) {
                            //do NOTHING task not found
                        }
                    LOG.log(task.name() ,Level.INFO);
                    switch(task) {
                        case PING:  ping(jsonObject);  break;
                        case GET: fetchFile(jsonObject); break;
                        case PUT: upload(jsonObject);break;
                        case LIST: listFiles(jsonObject);break;
                        case DELETE: remove(jsonObject);break;
                        case RENAME: rename(jsonObject);break;
                        case MKDIR: createDir(jsonObject);break;
                        case RMDIR: removeDir(jsonObject);break;
                        case DEFAULT: throw new Exception("Task not found");
                    }


                } catch (SocketException e) {
                    e.printStackTrace();
                    throw new Exception(e);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new Exception(e);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new Exception(e);
                }finally {
                    try {
                        if (this.logged) this.client.logout();
                        this.client.disconnect();
                        LOG.log("Disconnected",Level.INFO);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new Exception(e);
                    }
                }

		} catch (Exception e) {
			response.setData(this.FTPError(e.toString()));
        	SECErrorManager errorManager = new SECErrorManager();
        	errorManager.logError(e.toString(), Level.SEVERE);
			e.printStackTrace();
		}
	}
	
	@Override
	public Response getResponse() {
		return response;
	}
	
}

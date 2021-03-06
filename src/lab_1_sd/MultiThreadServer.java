
package lab_1_sd;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiThreadServer implements Runnable {
   
    Socket csocket;
    // Arreglo con las particiones del cache
    ArrayList<LRUCache> particiones;
    private String fromClient;

    MultiThreadServer(Socket csocket, ArrayList<LRUCache> Particiones) {
        this.csocket = csocket;  
        this.particiones = Particiones;
    }
   
    @Override
    public void run() {
        try {     
            //Buffer para leer al cliente
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));
            //Buffer para enviar al cliente
            DataOutputStream outToClient = new DataOutputStream(csocket.getOutputStream());

            //Recibimos el dato del cliente y lo mostramos en el server
            fromClient =inFromClient.readLine();
            System.out.println("===== ===== ===== ===== =====");

//            String[] tokens = fromClient.split(" ");
            String[] tokens = fromClient.split("/");
//            String parametros = tokens[1];
//
            String http_method = tokens[0].trim();
            String resource    = tokens[1].trim();
            String id          = tokens[2].trim();
            String meta_data   = "";
            if (tokens.length>3) {
                meta_data   = tokens[3].trim();
            }
        
            System.out.println("CONSULTA:       " + fromClient);
            System.out.println("HTTP METHOD:    " + http_method);
            System.out.println("Resource:       " + resource);
            System.out.println("ID:             " + id);
            System.out.println("META DATA:      " + meta_data);  
            
            // Determinamos la particion a acceder con una funcion hash
            int ParticionDestino = hash(id, particiones.size()-1);
            System.out.println("Particion destino:"+ParticionDestino);

            switch (http_method) {
                case "GET":
                    
                    System.out.println("Buscando en el cache de '" + resource + "' el registro con id " + id);
                    // buscar en el cache estatico la respuesta a la query
                    String result = particiones.get(particiones.size()-1).getEntryFromCache(id);

                    if(result != null){//Si está
                        System.out.println("Entrada en el cache estático - Particion: "+(particiones.size()-1));
                        // Mostramos el cache(querys y answers)
                        particiones.get(particiones.size()-1).print(); System.out.println("");
                    }else{ //Si no está en el caché estático, verifico a la particion correspondiente
                        result = particiones.get(ParticionDestino).getEntryFromCache(id);
                        // Mostramos el cache(querys y answers)
                        particiones.get(ParticionDestino).print(); System.out.println("");
                    }
                    if (result == null) { // MISS
                        System.out.println("MISS");
                        //Enviamos miss al cliente
                        outToClient.writeBytes("MISS\n");
                    }else{
                        System.out.println("HIT");
                        // Enviamos hit al cliente
                        outToClient.writeBytes(result+"\n");
                    }
                    break;
                case "POST":
                    System.out.println("Mensaje desde IndexService");
                    System.out.println("Agregamos:\n    query : " + id + "\n    answer: " + meta_data);
                    
                    // buscar en el cache estatico la respuesta a la query
                    result = particiones.get(particiones.size()-1).getEntryFromCache(id);
                    if (result == null) {//si no está
                        // Metodo sincrono para agregar la query con su respuesta al cache
                        syncPost(ParticionDestino, id, meta_data);  
                        // Mostramos el cache(querys y answers)
                        particiones.get(ParticionDestino).print(); System.out.println("");

                    }else{//Si esta
                        System.out.println("La entrada está en la parte estática del cache");
                        // Mostramos el cache(querys y answers)
                        particiones.get(particiones.size()-1).print(); System.out.println("");
                    }
                    
                    outToClient.writeBytes("Agregado\n");                   

                    break;
                case "PUT":                  
                    System.out.println("Mensaje desde IndexService");
                    System.out.println("Actualizamos:\n    query : " + id + "\n    answer: " + meta_data);
                   
                    //Se busca entrada en el cache estatico
                    result = particiones.get(particiones.size()-1).getEntryFromCache(id);
                    
                    if (result == null) {//Si no está
                        //Se busca si está en la particion correspondiente
                        result = particiones.get(ParticionDestino).getEntryFromCache(id);
                        if (result == null) {//si no está
                            System.out.println("Entrada no existe");
                            particiones.get(ParticionDestino).print(); System.out.println("");
                            outToClient.writeBytes("No actualizado, entrada no existe.\n");
                        }else{//Si está
                            // Metodo sincrono para actualizar la respuesta de la query
                            syncPut(ParticionDestino, id, meta_data);
                            // Mostramos el cache(querys y answers)
                            particiones.get(ParticionDestino).print(); System.out.println("");
                            outToClient.writeBytes("Actualizado.\n");
                        }

                    }else{
                        System.out.println("La entrada está en la parte estática del cache");
                        syncPut(particiones.size()-1, id, meta_data);
                        // Mostramos el cache(querys y answers)
                        particiones.get(particiones.size()-1).print(); System.out.println("");
                        outToClient.writeBytes("Actualizado.\n");
                    }
                    
                    break;
                case "DELETE":
                    System.out.println("Mensaje desde IndexService");
                    System.out.println("Borrando el recurso de tipo '" + resource + "' con id " + id);
                    
                    //Se busca entrada en el cache estatico
                    result = particiones.get(particiones.size()-1).getEntryFromCache(id);
                    
                    if (result == null) {//Si no está
                        //Se busca si está en la particion correspondiente
                        result = particiones.get(ParticionDestino).getEntryFromCache(id);
                        if (result == null) {//si no está
                            System.out.println("Entrada no existe");
                            particiones.get(ParticionDestino).print(); System.out.println("");
                            outToClient.writeBytes("No eliminado, entrada no existe.\n");
                        }
                        else{//Si está
                            // Metodo sincrono para eliminar la respuesta de la query
                            syncDelete(ParticionDestino, id);
                            // Mostramos el cache(querys y answers)
                            particiones.get(ParticionDestino).print();
                            outToClient.writeBytes("Eliminado.\n");
                        }
                    }else{
                        System.out.println("La entrada está en la parte estática del cache");
                        // Metodo sincrono para eliminar la respuesta de la query
                        syncDelete(particiones.size()-1, id);
                        // Mostramos el cache(querys y answers)
                        particiones.get(particiones.size()-1).print(); System.out.println("");
                        outToClient.writeBytes("Eliminado.\n");
                    }
                    break;
                default:
                    System.out.println("Not a valid HTTP Request");
                    break;
            }
        } catch (IOException ex) {
            Logger.getLogger(MultiThreadServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private int hash(String id, int size) {
        int hash = 13;
        for (int i = 0; i < id.length(); i++) {
            hash = hash*31 + id.charAt(i);
        }
        // Nos aseguramos de que sea positivo
        hash = (int) Math.sqrt(hash*hash);
        // Determinamos la particion a ocupar
        hash = hash%size;
        
        return hash;
    }

    private synchronized void syncPost(int ParticionDestino, String id, String meta_data) {
        particiones.get(ParticionDestino).addEntryToCache(id, meta_data);
    }
    
    private synchronized void syncPut(int ParticionDestino, String id, String meta_data) {
        particiones.get(ParticionDestino).updateAnswerFromCache(id, meta_data);
    }

    private void syncDelete(int ParticionDestino, String id) {
        particiones.get(ParticionDestino).removeEntryFromCache(id);
    }
    
}

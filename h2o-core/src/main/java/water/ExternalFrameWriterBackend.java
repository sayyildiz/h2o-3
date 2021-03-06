package water;

import water.fvec.ChunkUtils;
import water.fvec.NewChunk;

import java.io.IOException;
import java.nio.channels.ByteChannel;

import static water.ExternalFrameUtils.*;

/**
 * This class contains methods used on the H2O backend to store incoming data as H2O Frame
 * from non-H2O environment
 */
final class ExternalFrameWriterBackend {

  static void initFrame(ByteChannel sock, AutoBuffer ab) throws IOException {
    String frameKey = ab.getStr();
    String[] names = ab.getAStr();
    ChunkUtils.initFrame(frameKey, names);
    notifyRequestFinished(sock, ExternalBackendRequestType.INIT_FRAME.getByte());
  }

  static void finalizeFrame(ByteChannel sock, AutoBuffer ab) throws IOException {
    String keyName = ab.getStr();
    long[] rowsPerChunk = ab.getA8();
    byte[] colTypes = ab.getA1();
    String[][] colDomains = ab.getAAStr();
    ChunkUtils.finalizeFrame(keyName, rowsPerChunk, colTypes, colDomains);
    notifyRequestFinished(sock, ExternalBackendRequestType.FINALIZE_FRAME.getByte());
  }
  
    static void writeToChunk(ByteChannel sock, AutoBuffer ab) throws IOException {
        String frameKey = ab.getStr();
        byte[] expectedTypes = ab.getA1();
        if( expectedTypes == null){
          throw new RuntimeException("Expected types can't be null.");
        }
        int[] maxVecSizes = ab.getA4();

        int[] elemSizes = ExternalFrameUtils.getElemSizes(expectedTypes, maxVecSizes != null ? maxVecSizes : EMPTY_ARI);
        int[] startPos = ExternalFrameUtils.getStartPositions(elemSizes);
        byte[] vecTypes = vecTypesFromExpectedTypes(expectedTypes, maxVecSizes != null ? maxVecSizes : EMPTY_ARI);
        int expectedNumRows = ab.getInt();
        int currentRowIdx = 0;
        int chunk_id = ab.getInt();
        NewChunk[] nchnk = ChunkUtils.createNewChunks(frameKey, vecTypes, chunk_id);
        assert nchnk != null;
        while (currentRowIdx < expectedNumRows) {
            for(int typeIdx = 0; typeIdx < expectedTypes.length; typeIdx++){
                switch (expectedTypes[typeIdx]) {
                    case EXPECTED_BOOL: // fall through to byte since BOOL is internally stored in frame as number (byte)
                    case EXPECTED_BYTE:
                        store(ab, nchnk[startPos[typeIdx]], ab.get1());
                        break;
                    case EXPECTED_CHAR:
                        store(ab, nchnk[startPos[typeIdx]], ab.get2());
                        break;
                    case EXPECTED_SHORT:
                        store(ab, nchnk[startPos[typeIdx]], ab.get2s());
                        break;
                    case EXPECTED_INT:
                        store(ab, nchnk[startPos[typeIdx]], ab.getInt());
                        break;
                    case EXPECTED_TIMESTAMP: // fall through to long since TIMESTAMP is internally stored in frame as long
                    case EXPECTED_LONG:
                        store(ab, nchnk[startPos[typeIdx]], ab.get8());
                        break;
                    case EXPECTED_FLOAT:
                        store(nchnk[startPos[typeIdx]], ab.get4f());
                        break;
                    case EXPECTED_DOUBLE:
                        store(nchnk[startPos[typeIdx]], ab.get8d());
                        break;
                    case EXPECTED_STRING:
                        store(ab, nchnk[startPos[typeIdx]], ab.getStr());
                        break;
                    case EXPECTED_VECTOR:
                        storeVector(ab, nchnk, elemSizes[typeIdx], startPos[typeIdx]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown expected type: " + expectedTypes[typeIdx]);
                }
            }
            currentRowIdx++;
        }
        ChunkUtils.closeNewChunks(nchnk);
        notifyRequestFinished(sock, ExternalBackendRequestType.WRITE_TO_CHUNK.getByte());
    }

    private static void storeVector(AutoBuffer ab, NewChunk[] nchnk, int maxVecSize, int startPos){
      boolean isSparse = ab.getZ();
      if(isSparse){
        int[] indices = ab.getA4();
        double[] values = ab.getA8d();

        if(values == null){
          throw new RuntimeException("Values of sparse Vector can't be null!");
        }
        if(indices == null){
          throw new RuntimeException("Indices of sparse Vector can't be null!");
        }

        // store values
        int zeroSectionStart = 0;
        for(int i = 0; i < indices.length; i++){
          for(int zeroIdx = zeroSectionStart; zeroIdx < indices[i]; zeroIdx++ ){
            store(nchnk[startPos + zeroIdx], 0);
          }
          store(nchnk[startPos + indices[i]], values[i]);
          zeroSectionStart = indices[i] + 1;
        }

        // fill remaining zeros
        for(int j = zeroSectionStart; j < maxVecSize; j++) {
          store(nchnk[startPos + j], 0);
        }
      } else {
        double[] values = ab.getA8d();
        if(values == null){
          throw new RuntimeException("Values of dense Vector can't be null!");
        }
        // fill values
        for(int j = 0; j < values.length; j++){
          store(nchnk[startPos + j], values[j]);
        }

        // fill remaining zeros
        for(int j = values.length; j < maxVecSize; j++){
          store(nchnk[startPos + j], 0);
        }
      }
    }

    private static void store(AutoBuffer ab, NewChunk chunk, long data){
        if(isNA(ab, data)){
            chunk.addNA();
        }else{
            chunk.addNum(data);
        }
    }

    private static void store(NewChunk chunk, double data){
        if(isNA(data)){
            chunk.addNA();
        }else{
            chunk.addNum(data);
        }
    }

    private static void store(AutoBuffer ab, NewChunk chunk, String data){
        if(isNA(ab, data)){
            chunk.addNA();
        }else{
            chunk.addStr(data);
        }
    }

  private static void notifyRequestFinished(ByteChannel sock, byte confirmation) throws IOException {
    AutoBuffer ab = new AutoBuffer();
    ab.put1(confirmation);
    writeToChannel(ab, sock);
  }
}

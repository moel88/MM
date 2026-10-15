package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.AtomicTimeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AtomicFlashMeshTest {
 @Test void currentReviewHasNoOpaqueWhiteOverlay(){
  AtomicFlashMesh.Quads noDraw=(a,b,c,d,e,f,g,h,color)->fail("Violet world must remain visible");
  for(float a=-10;a<AtomicTimeline.END+20;a+=.5f)assertFalse(AtomicFlashMesh.draw(a,640,360,noDraw));
 }
 @Test void invalidViewportAndNonfiniteAgeNeverDraw(){
  AtomicFlashMesh.Quads noDraw=(a,b,c,d,e,f,g,h,color)->fail("Unexpected overlay");
  assertFalse(AtomicFlashMesh.draw(128,0,360,noDraw));assertFalse(AtomicFlashMesh.draw(128,640,-1,noDraw));assertFalse(AtomicFlashMesh.draw(Float.NaN,640,360,noDraw));
 }
}

package gbhdmi 

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{GbConst, HVSync}

class MemHdmi extends Module with GbConst with GbHdmiConst {
  val io = IO(new Bundle {
    /* memory read interface */
    val mem_addr  = Output(UInt((log2Ceil(GBWIDTH*GBHEIGHT)).W))
    val mem_data  = Input(UInt(2.W))
    val mem_read = Output(Bool())

    /* Video output signals */
    val video_hsync = Output(Bool())
    val video_vsync = Output(Bool())
    val video_color = Output(new HdmiColors())
    val video_de = Output(Bool())
  })

  println("TODO: change video geometry")
  val hvsync = Module(new HVSync()) // Synchronize VGA module
  io.video_hsync := hvsync.io.hsync
  io.video_vsync := hvsync.io.vsync

  val xpos = (hvsync.H_DISPLAY - GBWIDTH.U)/2.U
  val ypos = (hvsync.V_DISPLAY - GBHEIGHT.U)/2.U

  val gb_display = hvsync.io.display_on & (hvsync.io.vpos > ypos) & (hvsync.io.hpos > xpos)

  val gblines = RegInit(0.U((log2Ceil(GBWIDTH)).W))
  val gbcols = RegInit(0.U((log2Ceil(GBHEIGHT)).W))
  val gbpix = RegInit(0.U((log2Ceil(GBWIDTH*GBHEIGHT)).W))

  /* state machine */
  val sInit :: sPixInc :: sLineInc :: sWait :: Nil = Enum(4)
  val state = RegInit(sInit)

  switch(state) {
    is(sInit) {
      when(gb_display){
        state := sPixInc
      }
    }
    is(sPixInc) {
      when(gbcols >= (GBWIDTH - 1).U &&
           gblines <= (GBHEIGHT -1).U){
        state := sLineInc
      }
      when(gblines > (GBHEIGHT - 1).U){
        state := sWait
      }
    }
    is(sLineInc) {
          state := sWait
    }
    is(sWait) {
      when(!hvsync.io.hsync){
        when(gblines < GBHEIGHT.U) {
          state := sPixInc
        }
      }
      when(!hvsync.io.vsync){
        state := sInit
      }
    }
  }

  /* pixel count */
  when(gb_display){
    when(state===sPixInc) {
      gbpix := gbpix + 1.U
      gbcols := gbcols + 1.U
    }
    when(state===sLineInc) {
      gblines := gblines + 1.U
      gbcols := 0.U
    }
  }
  when(state===sInit) {
    gbpix := 0.U
    gbcols := 0.U
    gblines := 0.U
  }

  /* Vga colors */
  io.video_color := vga2hdmiColors(VGA_BLACK)
  io.video_de := false.B
  when(gb_display && (state===sPixInc)){
    io.video_color := vga2hdmiColors(GbColors(io.mem_data))
    io.video_de := true.B
  }

  /* Memory interface */
  io.mem_addr  := gbpix
  io.mem_read  := true.B

}

object MemHdmiDriver extends App {
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new MemHdmi())))
}

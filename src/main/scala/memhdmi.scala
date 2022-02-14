package gbhdmi 

import chisel3._
import chisel3.util._
import chisel3.util.log2Ceil
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{GbConst, HVSync, VideoParams}

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

    val vp = VideoParams(
        H_DISPLAY = 1280, H_FRONT = 110,
        H_SYNC = 40, H_BACK = 220,
        V_SYNC = 5,  V_BACK = 20,
        V_TOP = 5, V_DISPLAY = 720,
        V_BOTTOM = 20)

    val hv_sync = Module(new HVSync(vp)) // Synchronize VGA module
    io.video_hsync := hv_sync.io.hsync
    io.video_vsync := hv_sync.io.vsync
    io.video_de    := hv_sync.io.display_on

    val wpix = 4
    val hpix = 4

    val xpos = (hv_sync.H_DISPLAY - (wpix*GBWIDTH).U)/2.U
    val ypos = (hv_sync.V_DISPLAY - (hpix*GBHEIGHT).U)/2.U

    val gb_display = hv_sync.io.display_on & (hv_sync.io.vpos > ypos) & (hv_sync.io.hpos > xpos)

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
        when(!hv_sync.io.hsync){
          when(gblines < GBHEIGHT.U) {
            state := sPixInc
          }
        }
        when(!hv_sync.io.vsync){
          state := sInit
        }
      }
    }

    /* pixel count */
    val newgbline = ((hv_sync.io.hpos % hpix.U) === (hpix-1).U)
    val newgbcol = ((hv_sync.io.vpos % wpix.U) === (wpix-1).U)

    when(gb_display){
      when((state===sPixInc) && newgbline) {
        gbpix := gbpix + 1.U
        gbcols := gbcols + 1.U
      }
      when(state===sLineInc) {
        when(newgbcol){
            gblines := gblines + 1.U
            gbpix := gbpix + 1.U
        }.otherwise {
            gbpix := gbpix - GBWIDTH.U + 1.U
        }
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
    when(gb_display && (state =/= sWait)){
//      io.video_color := vga2hdmiColors(GbColors(io.mem_data))
//      io.video_color := vga2hdmiColors(GbPocket(io.mem_data))
      io.video_color := vga2hdmiColors(GbPink(io.mem_data))
    }

    /* Memory interface */
    io.mem_addr  := gbpix
    io.mem_read  := true.B

}

object MemHdmiDriver extends App {
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new MemHdmi())))
}

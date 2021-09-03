package gbhdmi 

import chisel3._
import chisel3.util._
import chisel3.util.log2Ceil
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{GbConst, HVSync, VideoParams}

class ColorPattern extends Module with GbConst with GbHdmiConst {
    val io = IO(new Bundle {
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
        V_BOTTOM = 5)

    val hv_sync = Module(new HVSync(vp)) // Synchronize VGA module
    io.video_hsync := hv_sync.io.hsync
    io.video_vsync := hv_sync.io.vsync
    io.video_de    := hv_sync.io.display_on
  
    io.video_color := vga2hdmiColors(VGA_BLACK)
    when(hv_sync.io.display_on){
      io.video_color := vga2hdmiColors(GB_GREEN3)
    }
}
